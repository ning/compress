package com.ning.compress.gzip;

import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

import com.ning.compress.*;

/**
 * {@link com.ning.compress.Uncompressor} implementation for uncompressing
 * GZIP encoded data in "push" mode, in which input is not
 * read using {@link java.io.InputStream} but rather pushed to
 * uncompressor in variable length chunks.
 */
public class GZIPUncompressor extends Uncompressor
{
    /*
    ///////////////////////////////////////////////////////////////////////
    // GZIP constants
    ///////////////////////////////////////////////////////////////////////
     */

    // little-endian marker bytes:
    protected final static int GZIP_MAGIC = 0x8b1f;

    protected final static byte GZIP_MAGIC_0 = (byte) (GZIP_MAGIC & 0xFF);
    protected final static byte GZIP_MAGIC_1 = (byte) (GZIP_MAGIC >> 8);
    
    // // // File header flags.

    //protected final static int FTEXT    = 1;    // Extra text
    protected final static int FHCRC      = 2;    // Header CRC
    protected final static int FEXTRA     = 4;    // Extra field
    protected final static int FNAME      = 8;    // File name
    protected final static int FCOMMENT   = 16;   // File comment

    /**
     * Size of input buffer for compressed input data.
     */
    protected final static int INPUT_BUFFER_SIZE = 8192;

    /**
     * For decoding we should use buffer that is big enough
     * to contain typical amount of decoded data; 64k seems
     * like a nice big number
     */
    protected final static int DECODE_BUFFER_SIZE = 0xFFFF;
        
    /*
    ///////////////////////////////////////////////////////////////////////
    // State constants
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * State in which a new compression stream can start.
     */
    protected final static int STATE_INITIAL = 0;

    // State in which first byte of signature has been matched, second exepcted
    protected final static int STATE_HEADER_SIG1 = 1;

    // State in which 'compression type' byte is expected
    protected final static int STATE_HEADER_COMP_TYPE = 2;
    // State in which flag byte is expected
    protected final static int STATE_HEADER_FLAGS = 3;
    // State in which we are to skip 6 bytes 
    protected final static int STATE_HEADER_SKIP = 4;
    protected final static int STATE_HEADER_EXTRA0 = 5;
    protected final static int STATE_HEADER_EXTRA1 = 6;
    protected final static int STATE_HEADER_FNAME = 7;
    protected final static int STATE_HEADER_COMMENT = 8;
    protected final static int STATE_HEADER_CRC0 = 9;
    protected final static int STATE_HEADER_CRC1 = 10;

    
    protected final static int STATE_TRAILER_CRC0 = 11;
    protected final static int STATE_TRAILER_CRC1 = 12;
    protected final static int STATE_TRAILER_CRC2 = 13;
    protected final static int STATE_TRAILER_CRC3 = 14;
    protected final static int STATE_TRAILER_LEN0 = 15;
    protected final static int STATE_TRAILER_LEN1 = 16;
    protected final static int STATE_TRAILER_LEN2 = 17;
    protected final static int STATE_TRAILER_LEN3 = 18;

    /**
     * State in which we are buffering compressed data for decompression
     */
    protected final static int STATE_BODY = 20;
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Configuration, helper objects
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Handler that will receive uncompressed data.
     */
    protected final DataHandler _handler;

    /**
     * Object that handles details of buffer recycling
     */
    protected final BufferRecycler _recycler;

    protected final GZIPRecycler _gzipRecycler;

    protected Inflater _inflater;
    
    protected final CRC32 _crc;
   
    /**
     * Buffer in which compressed input is buffered if necessary, to get
     * full chunks for decoding.
     */
    protected byte[] _inputBuffer;
    
    /**
     * Buffer used for data uncompressed from <code>_inputBuffer</code>.
     */
    protected byte[] _decodeBuffer;
    
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Decoder state
    ///////////////////////////////////////////////////////////////////////
     */
    
    /**
     * Current decoding state, which determines meaning of following byte(s).
     */
    protected int _state = STATE_INITIAL;


    /**
     * Header flags read from gzip header
     */
    protected int _flags;

    /**
     * Expected CRC for header, from gzip file itself.
     */
    protected int _headerCRC;
    
    /**
     * Simple counter used when skipping fixed number of bytes
     */
    protected int _skippedBytes;
    
    /**
     * CRC container in trailer, should match calculated CRC over data
     */
    protected int _trailerCRC;

    /**
     * Number of bytes that trailer indicates preceding data stream
     * should have had.
     */
    protected int _trailerCount;
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Instance creation
    ///////////////////////////////////////////////////////////////////////
     */
    
    public GZIPUncompressor(DataHandler h)
    {
        _handler = h;
        _recycler = BufferRecycler.instance();
        _inputBuffer = _recycler.allocInputBuffer(INPUT_BUFFER_SIZE);
        _decodeBuffer = _recycler.allocDecodeBuffer(DECODE_BUFFER_SIZE);
        _gzipRecycler = GZIPRecycler.instance();
        _inflater = _gzipRecycler.allocInflater();
        _crc = new CRC32();
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Uncompressor API implementation
    ///////////////////////////////////////////////////////////////////////
     */
    @Override
    public void feedCompressedData(byte[] comp, int offset, int len) throws IOException
    {
        final int end = offset + len;
        if (_state != STATE_BODY) {
            if (_state < STATE_BODY) { // header
                offset = _handleHeader(comp, offset, len);
                if (offset >= end) { // not fully handled yet
                    return;
                }
                _crc.reset();
            } else { // trailer
                offset = _handleTrailer(comp, offset, len);
                if (offset < end) { // sanity check
                    throw new IllegalStateException();
                }
                return;
            }
        }
    }
    
    private final int _handleHeader(byte[] comp, int offset, int len) throws IOException
    {        
        final int end = offset + len;
        main_loop:
        while (offset < end && _state < STATE_BODY) {
            byte b = comp[offset++];
            _crc.update(b);
            
            switch (_state) {
            case STATE_INITIAL:
                if (b != GZIP_MAGIC_0) {
                    _reportBadHeader(comp, offset, len, 0);
                }
                if (offset >= end) {
                    _state = STATE_HEADER_SIG1;
                    break;
                }
                b = comp[offset++];
                _crc.update(b);
                // fall through
            case GZIP_MAGIC_0:
                if (b != GZIP_MAGIC_1) {
                    _reportBadHeader(comp, offset, len, 1);
                }
                if (offset >= end) {
                    _state = STATE_HEADER_COMP_TYPE;
                    break;
                }
                b = comp[offset++];
                _crc.update(b);
                // fall through
            case STATE_HEADER_COMP_TYPE:
                if (b != Deflater.DEFLATED) {
                    _reportBadHeader(comp, offset, len, 1);
                }
                if (offset >= end) {
                    _state = STATE_HEADER_FLAGS;
                    break;
                }
                b = comp[offset++];
                _crc.update(b);
                // fall through
            case STATE_HEADER_FLAGS:
                _flags = b; // should we validate these?
                _skippedBytes = 0;
                if (offset >= end) {
                    _state = STATE_HEADER_SKIP;
                    break;
                }
                b = comp[offset++];
                _crc.update(b);
                // fall through
            case STATE_HEADER_SKIP:
                while (++_skippedBytes < 6) {
                    if (offset >= end) {
                        break main_loop;
                    }
                    b = comp[offset++];
                    _crc.update(b);
                }
                if (_hasFlag(FEXTRA)) {
                    _state = STATE_HEADER_EXTRA0;
                } else if (_hasFlag(FNAME)) {
                    _state = STATE_HEADER_FNAME;
                } else if (_hasFlag(FCOMMENT)) {
                    _state = STATE_HEADER_COMMENT;
                } else if (_hasFlag(FHCRC)) {
                    _state = STATE_HEADER_CRC0;
                } else { // no extras... body, I guess?
                    _state = STATE_BODY;
                }
                // let's keep things simple, do explicit re-loop to sort it out:
                continue;
            case STATE_HEADER_EXTRA0:
                _state = STATE_HEADER_EXTRA1;
                break;
            case STATE_HEADER_EXTRA1:
                if (_hasFlag(FNAME)) {
                    _state = STATE_HEADER_FNAME;
                } else if (_hasFlag(FCOMMENT)) {
                    _state = STATE_HEADER_COMMENT;
                } else if (_hasFlag(FHCRC)) {
                    _state = STATE_HEADER_CRC0;
                } else {
                    _state = STATE_BODY;
                }
                break;
            case STATE_HEADER_FNAME: // skip until zero
                while (b != 0) {
                    if (offset >= end) {
                        break main_loop;
                    }
                    b = comp[offset++];
                    _crc.update(b);
                }
                if (_hasFlag(FCOMMENT)) {
                    _state = STATE_HEADER_COMMENT;
                } else if (_hasFlag(FHCRC)) {
                    _state = STATE_HEADER_CRC0;
                } else {
                    _state = STATE_BODY;
                }
                break;
            case STATE_HEADER_COMMENT:
                while (b != 0) {
                    if (offset >= end) {
                        break main_loop;
                    }
                    b = comp[offset++];
                }
                if (_hasFlag(FHCRC)) {
                    _state = STATE_HEADER_CRC0;
                } else {
                    _state = STATE_BODY;
                }
                break;
            case STATE_HEADER_CRC0:
                _headerCRC = b & 0xFF;
                if (offset >= end) {
                    _state = STATE_HEADER_CRC1;
                    break;
                }
                b = comp[offset++];
                _crc.update(b);
                // fall through
            case STATE_HEADER_CRC1:
                _headerCRC += ((b & 0xFF) << 8);
                int act = (int)_crc.getValue() & 0xffff;
                if (act != _headerCRC) {
                    throw new ZipException("Corrupt GZIP header (header CRC 0x"
                                          +Integer.toHexString(act)+", expected 0x "
                                          +Integer.toHexString(_headerCRC));
                }
                _state = STATE_BODY;
                break;
            }
        }
        return offset;
    }
    
    private final int _handleTrailer(byte[] comp, int offset, int len) throws IOException
    {
        final int end = offset + len;

        while (offset < end) {
            byte b = comp[offset++];
            _crc.update(b);

            switch (b) {
            case STATE_TRAILER_CRC0:
                _trailerCRC = b & 0xFF;
                _state = STATE_HEADER_CRC1;
                break;
            case STATE_TRAILER_CRC1:
                _trailerCRC += (b & 0xFF) << 8;
                _state = STATE_TRAILER_CRC2;
                break;
            case STATE_TRAILER_CRC2:
                _trailerCRC += (b & 0xFF) << 16;
                _state = STATE_TRAILER_CRC3;
                break;
            case STATE_TRAILER_CRC3:
                _trailerCRC += (b & 0xFF) << 24;
                // verify CRC:
                int actCrc = (int) _crc.getValue();
                if (_trailerCRC != actCrc) {
                    throw new ZipException("Corrupt block or trailer: expected CRC "+Integer.toHexString(_trailerCRC)+", computed "+Integer.toHexString(actCrc));
                }
                _state = STATE_TRAILER_LEN0;
                break;
            case STATE_TRAILER_LEN0:
                _trailerCount = b & 0xFF;
                _state = STATE_TRAILER_LEN1;
                break;
            case STATE_TRAILER_LEN1:
                _trailerCount += (b & 0xFF) << 8;
                _state = STATE_TRAILER_LEN2;
                break;
            case STATE_TRAILER_LEN2:
                _trailerCount += (b & 0xFF) << 16;
                _state = STATE_TRAILER_LEN3;
                break;
            case STATE_TRAILER_LEN3:
                _trailerCount += (b & 0xFF) << 24;
                _state = STATE_INITIAL;
                // Verify count...
                int actCount32 = (int) _inflater.getBytesWritten();

                if (actCount32 != _trailerCount) {
                    throw new ZipException("Corrupt block or trailed: expected byte count "+_trailerCount+", read "+actCount32);
                }
                break;
            }
        }
        return offset;
    }

    @Override
    public void complete() throws IOException
    {
        byte[] b = _inputBuffer;
        if (b != null) {
            _inputBuffer = null;
            _recycler.releaseInputBuffer(b);
        }
        b = _decodeBuffer;
        if (b != null) {
            _decodeBuffer = null;
            _recycler.releaseDecodeBuffer(b);
        }
        Inflater i = _inflater;
        if (i != null) {
            _inflater = null;
            _gzipRecycler.releaseInflater(i);
        }
        if (_state != STATE_INITIAL) {
            if (_state >= STATE_BODY) {
                if (_state == STATE_BODY) {
                    throw new ZipException("Invalid GZIP stream: end-of-input in the middle of compressed data");
                }
                throw new ZipException("Invalid GZIP stream: end-of-input in the trailer (state: "+_state+")");
            }
            throw new ZipException("Invalid GZIP stream: end-of-input in header (state: "+_state+")");
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
     */

    protected final boolean _hasFlag(int flag) {
        return (_flags & flag) == flag;
    }
    
    protected void _reportBadHeader(byte[] comp, int nextOffset, int len, int relative)
            throws IOException
    {
        String byteStr = "0x"+Integer.toHexString(comp[nextOffset] & 0xFF);
        if (relative <= 1) {
            int exp = (relative == 0) ? (GZIP_MAGIC & 0xFF) : (GZIP_MAGIC >> 8);
            --nextOffset;
            throw new ZipException("Bad GZIP stream: byte #"+relative+" of header not '"
                    +exp+"' (0x"+Integer.toHexString(exp)+") but "+byteStr);
        }
        if (relative == 2) { // odd that 
            throw new ZipException("Bad GZIP stream: byte #2 of header invalid: type "+byteStr
                    +" not supported, 0x"+Integer.toHexString(Deflater.DEFLATED)
                    +" expected");
        }
        throw new IOException("Bad GZIP stream: byte #"+relative+" of header invalid: "+byteStr);
    }
}
