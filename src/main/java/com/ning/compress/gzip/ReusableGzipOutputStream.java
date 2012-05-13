package com.ning.compress.gzip;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Optimized variant of {@link java.util.zip.GZIPOutputStream} that
 * reuses underlying {@link java.util.zip.Deflater} instance}.
 */
public class ReusableGzipOutputStream
    extends OutputStream
{
    /**
     * GZIP header magic number.
     */
    private final static int GZIP_MAGIC = 0x8b1f;
    
    /**
     * For now, static header seems fine, since JDK default gzip writer
     * does it too:
     */
    final static byte[] DEFAULT_HEADER = new byte[] {
        (byte) GZIP_MAGIC,                // Magic number (short)
        (byte)(GZIP_MAGIC >> 8),          // Magic number (short)
        Deflater.DEFLATED,                // Compression method (CM)
        0,                                // Flags (FLG)
        0,                                // Modification time MTIME (int)
        0,                                // Modification time MTIME (int)
        0,                                // Modification time MTIME (int)
        0,                                // Modification time MTIME (int)
        0,                                // Extra flags (XFLG)
        (byte) 0xff                       // Operating system (OS), UNKNOWN
    };
    
    protected final byte[] _eightByteBuffer = new byte[8];
    
    protected Deflater _deflater;
    
    /**
     * Underlying output stream that header, compressed content and
     * footer go to
     */
    protected OutputStream _rawOut;
    
    protected DeflaterOutputStream _deflaterOut;
    
    protected CRC32 _crc;
    
    public ReusableGzipOutputStream()
    {
        super();
    }
    
    /*
    //////////////////////////////////////////////////
    // OutputStream API
    //////////////////////////////////////////////////
     */

    @Override
    public void close() throws IOException
    {
        _deflaterOut.finish();
        _writeTrailer(_rawOut);
        _rawOut.close();
    }
    
    @Override
    public void flush() throws IOException {
        _deflaterOut.flush();
    }
    
    @Override
    public final void write(byte[] buf) throws IOException {
        write(buf, 0, buf.length);
    }
    
    @Override
    public final void write(int c) throws IOException {
        _eightByteBuffer[0] = (byte) c;
        write(_eightByteBuffer, 0, 1);
    }
    
    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
        _deflaterOut.write(buf, off, len);
        _crc.update(buf, off, len);
    }
    
    /*
    //////////////////////////////////////////////////
    // Extended API
    //////////////////////////////////////////////////
     */
    
    public void initialize(OutputStream compOut) throws IOException
    {
        _rawOut = compOut;
        // write header:
        _rawOut.write(DEFAULT_HEADER);
        // construct deflater stream we need:
        if (_deflater == null) {
            // lowest compression level (1)
            //_deflater = new Deflater(1, true);
            _deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        } else {
            _deflater.reset();
        }
        _deflaterOut = new DeflaterOutputStream(_rawOut, _deflater, 512);
        // and construct/clear out CRC
        if (_crc == null) {
            _crc = new CRC32();
        } else {
            _crc.reset();
        }
    }
    
    /*
    //////////////////////////////////////////////////
    // Internal
    //////////////////////////////////////////////////
     */
    
    private void _writeTrailer(OutputStream out) throws IOException
    {
        _putInt(_eightByteBuffer, 0, (int) _crc.getValue());
        _putInt(_eightByteBuffer, 4, _deflater.getTotalIn());
        out.write(_eightByteBuffer, 0, 8);
    }
    
    /**
     * Stupid GZIP, writes stuff in wrong order (not network, but x86)
     */
    private void _putInt(byte[] buf, int offset, int value)
    {
        buf[offset++] = (byte) (value);
        buf[offset++] = (byte) (value >> 8);
        buf[offset++] = (byte) (value >> 16);
        buf[offset] = (byte) (value >> 24);
    }
}