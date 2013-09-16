package com.ning.compress.lzf.util;
/*
 *
 * Copyright 2009-2013 Ning, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
*/


import java.io.*;

import com.ning.compress.BufferRecycler;
import com.ning.compress.lzf.*;

/**
 * Helper class that allows use of LZF compression even if a library requires
 * use of {@link FileInputStream}.
 *<p>
 * Note that use of this class is not recommended unless you absolutely must
 * use a {@link FileInputStream} instance; otherwise basic {@link LZFInputStream}
 * (which uses aggregation for underlying streams) is more appropriate
 *<p>
 *<p>
 * Implementation note: much of the code is just copied from {@link LZFInputStream},
 * so care must be taken to keep implementations in sync if there are fixes.
 * 
 * @since 0.8
 */
public class LZFFileInputStream
    extends FileInputStream
{
    /**
     * Underlying decoder in use.
     */
    protected final ChunkDecoder _decompressor;
    
    /**
     * Object that handles details of buffer recycling
     */
    protected final BufferRecycler _recycler;

    /**
     * Flag that indicates if we have already called 'inputStream.close()'
     * (to avoid calling it multiple times)
     */
    protected boolean _inputStreamClosed;
    
    /**
     * Flag that indicates whether we force full reads (reading of as many
     * bytes as requested), or 'optimal' reads (up to as many as available,
     * but at least one). Default is false, meaning that 'optimal' read
     * is used.
     */
    protected boolean _cfgFullReads = false;
        
    /**
     * the current buffer of compressed bytes (from which to decode)
     * */
    protected byte[] _inputBuffer;
        
    /**
     * the buffer of uncompressed bytes from which content is read
     * */
    protected byte[] _decodedBytes;
        
    /**
     * The current position (next char to output) in the uncompressed bytes buffer.
     * */
    protected int _bufferPosition = 0;
    
    /**
     * Length of the current uncompressed bytes buffer
     * */
    protected int _bufferLength = 0;

    /**
     * Wrapper object we use to allow decoder to read directly from the
     * stream, without ending in infinite loop...
     */
    protected final Wrapper _wrapper;
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Construction, configuration
    ///////////////////////////////////////////////////////////////////////
     */

    public LZFFileInputStream(File file) throws FileNotFoundException {
        this(file, ChunkDecoderFactory.optimalInstance());
    }

    public LZFFileInputStream(FileDescriptor fdObj) {
        this(fdObj, ChunkDecoderFactory.optimalInstance());
    }

    public LZFFileInputStream(String name) throws FileNotFoundException {
        this(name, ChunkDecoderFactory.optimalInstance());
    }
    
    public LZFFileInputStream(File file, ChunkDecoder decompressor) throws FileNotFoundException
    {
        super(file);
        _decompressor = decompressor;
        _recycler = BufferRecycler.instance();
        _inputStreamClosed = false;
        _inputBuffer = _recycler.allocInputBuffer(LZFChunk.MAX_CHUNK_LEN);
        _decodedBytes = _recycler.allocDecodeBuffer(LZFChunk.MAX_CHUNK_LEN);
        _wrapper = new Wrapper();
    }

    public LZFFileInputStream(FileDescriptor fdObj, ChunkDecoder decompressor)
    {
        super(fdObj);
        _decompressor = decompressor;
        _recycler = BufferRecycler.instance();
        _inputStreamClosed = false;
        _inputBuffer = _recycler.allocInputBuffer(LZFChunk.MAX_CHUNK_LEN);
        _decodedBytes = _recycler.allocDecodeBuffer(LZFChunk.MAX_CHUNK_LEN);
        _wrapper = new Wrapper();
    }

    public LZFFileInputStream(String name, ChunkDecoder decompressor) throws FileNotFoundException
    {
        super(name);
        _decompressor = decompressor;
        _recycler = BufferRecycler.instance();
        _inputStreamClosed = false;
        _inputBuffer = _recycler.allocInputBuffer(LZFChunk.MAX_CHUNK_LEN);
        _decodedBytes = _recycler.allocDecodeBuffer(LZFChunk.MAX_CHUNK_LEN);
        _wrapper = new Wrapper();
    }

    /**
     * Method that can be used define whether reads should be "full" or
     * "optimal": former means that full compressed blocks are read right
     * away as needed, optimal that only smaller chunks are read at a time,
     * more being read as needed.
     */
    public void setUseFullReads(boolean b) {
        _cfgFullReads = b;
    }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // FileInputStream overrides
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public int available()
    {
        if (_inputStreamClosed) { // javadocs suggest 0 for closed as well (not -1)
            return 0;
        }
        int left = (_bufferLength - _bufferPosition);
        return (left <= 0) ? 0 : left;
    }

    @Override
    public void close() throws IOException
    {
        _bufferPosition = _bufferLength = 0;
        byte[] buf = _inputBuffer;
        if (buf != null) {
            _inputBuffer = null;
            _recycler.releaseInputBuffer(buf);
        }
        buf = _decodedBytes;
        if (buf != null) {
            _decodedBytes = null;
            _recycler.releaseDecodeBuffer(buf);
        }
        if (!_inputStreamClosed) {
            _inputStreamClosed = true;
            super.close();
        }
    }

    // fine as is: don't override
    // public FileChannel getChannel();

    // final, can't override:
    //public FileDescriptor getFD();

    @Override
    public int read() throws IOException
    {
        if (!readyBuffer()) {
            return -1;
        }
        return _decodedBytes[_bufferPosition++] & 255;
    }

    @Override
    public int read(byte[] b) throws IOException
    {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException
    {
        if (!readyBuffer()) {
            return -1;
        }
        if (length < 1) {
            return 0;
        }
        // First let's read however much data we happen to have...
        int chunkLength = Math.min(_bufferLength - _bufferPosition, length);
        System.arraycopy(_decodedBytes, _bufferPosition, buffer, offset, chunkLength);
        _bufferPosition += chunkLength;

        if (chunkLength == length || !_cfgFullReads) {
            return chunkLength;
        }
        // Need more data, then
        int totalRead = chunkLength;
        do {
            offset += chunkLength;
            if (!readyBuffer()) {
                break;
            }
            chunkLength = Math.min(_bufferLength - _bufferPosition, (length - totalRead));
            System.arraycopy(_decodedBytes, _bufferPosition, buffer, offset, chunkLength);
            _bufferPosition += chunkLength;
            totalRead += chunkLength;
        } while (totalRead < length);

        return totalRead;
    }

    /**
     * Overridden to just skip at most a single chunk at a time
     */
    /*
    @Override
    public long skip(long n) throws IOException
    {
        if (!readyBuffer()) {
            return -1L;
        }
        int left = (_bufferLength - _bufferPosition);
        // either way, just skip whatever we have decoded
        if (left > n) {
            left = (int) n;
        }
        _bufferPosition += left;
        return left;
    }
    */

    /**
     * Overridden to implement efficient skipping by skipping full chunks whenever
     * possible.
     */
    @Override
    public long skip(long n) throws IOException
    {
        if (_inputStreamClosed) {
            return -1;
        }
        if (n <= 0L) {
            return n;
        }
        long skipped;

        // if any left to skip, just return that for simplicity
        if (_bufferPosition < _bufferLength) {
            int left = (_bufferLength - _bufferPosition);
            if (n <= left) { // small skip, fulfilled from what we already got
                _bufferPosition += (int) n;
                return n;
            }
            _bufferPosition = _bufferLength;
            skipped = left;
            n -= left;
        } else {
            skipped = 0L;
        }
        // and then full-chunk skipping, if possible
        while (true) {
            int amount = _decompressor.skipOrDecodeChunk(_wrapper, _inputBuffer, _decodedBytes, n);
            if (amount >= 0) { // successful skipping of the chunk
                skipped += amount;
                n -= amount;
                if (n <= 0L) {
                    return skipped;
                }
                continue;
            }
            if (amount == -1) { // EOF
                close();
                return skipped;
            }
            // decoded buffer-full, more than max skip
            _bufferLength = -(amount+1);
            skipped += n;
            _bufferPosition = (int) n;
            return skipped;
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Extended public API
    ///////////////////////////////////////////////////////////////////////
     */
    
    /**
     * Convenience method that will read and uncompress all data available,
     * and write it using given {@link OutputStream}. This avoids having to
     * make an intermediate copy of uncompressed data which would be needed
     * when doing the same manually.
     * 
     * @param out OutputStream to use for writing content
     * 
     * @return Number of bytes written (uncompressed)
     * 
     * @since 0.9.3
     */
    public int readAndWrite(OutputStream out) throws IOException
    {
        int total = 0;

        while (readyBuffer()) {
            int avail = _bufferLength - _bufferPosition;
            out.write(_decodedBytes, _bufferPosition, avail);
            _bufferPosition += avail; // to ensure it looks like we consumed it all
            total += avail;
        }
        return total;
    }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Fill the uncompressed bytes buffer by reading the underlying inputStream.
     * @throws IOException
     */
    protected boolean readyBuffer() throws IOException
    {
        if (_inputStreamClosed) {
            throw new IOException("Input stream closed");
        }
        if (_bufferPosition < _bufferLength) {
            return true;
        }
        _bufferLength = _decompressor.decodeChunk(_wrapper, _inputBuffer, _decodedBytes);
        if (_bufferLength < 0) {
            close();
            return false;
        }
        _bufferPosition = 0;
        return (_bufferPosition < _bufferLength);
    }

    protected final int readRaw(byte[] buffer, int offset, int length) throws IOException {
        return super.read(buffer, offset, length);
    }

    protected final long skipRaw(long amount) throws IOException {
        return super.skip(amount);
    }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper class(es)
    ///////////////////////////////////////////////////////////////////////
     */


    /**
     * This simple wrapper is needed to re-route read calls so that they will
     * use "raw" reads
     */
    private final class Wrapper extends InputStream
    {
        @Override
        public void close() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return readRaw(buffer, offset, length);
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            return readRaw(buffer, 0, buffer.length);
        }

        @Override
        public long skip(long n) throws IOException {
            return skipRaw(n);
        }
    }
}
