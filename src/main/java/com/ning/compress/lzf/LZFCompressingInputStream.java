package com.ning.compress.lzf;
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


import java.io.IOException;
import java.io.InputStream;

import com.ning.compress.BufferRecycler;
import com.ning.compress.lzf.util.ChunkEncoderFactory;

/**
 * Decorator {@link InputStream} implementation used for
 * reading <b>uncompressed</b> data
 * and <b>compressing</b> it on the fly, such that reads return compressed
 * data.
 * It is reverse of {@link LZFInputStream} (which instead uncompresses data).
 * 
 * @author Tatu Saloranta
 * 
 * @see com.ning.compress.lzf.LZFInputStream
 * 
 * @since 0.9.5
 */
public class LZFCompressingInputStream extends InputStream
{
    private final BufferRecycler _recycler;

    private ChunkEncoder _encoder;

    /**
     * Stream used for reading data to be compressed
     */
    protected final InputStream _inputStream;

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
     * Buffer in which uncompressed input is first read, before getting
     * encoded in {@link #_encodedBytes}.
     */
    protected byte[] _inputBuffer;

    /**
     * Buffer that contains compressed data that is returned to readers.
     */
    protected byte[] _encodedBytes;
    
    /**
     * The current position (next char to output) in the uncompressed bytes buffer.
     */
    protected int _bufferPosition = 0;
    
    /**
     * Length of the current uncompressed bytes buffer
     */
    protected int _bufferLength = 0;

    /**
     * Number of bytes read from the underlying {@link #_inputStream} 
     */
    protected int _readCount = 0;
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Construction, configuration
    ///////////////////////////////////////////////////////////////////////
     */
    
    public LZFCompressingInputStream(InputStream in)
    {
        this(null, in);
    }

    /**
     * @since 0.9.8
     */
    public LZFCompressingInputStream(final ChunkEncoder encoder, InputStream in)
    {
        // may be passed by caller, or could be null
        _encoder = encoder;
        _inputStream = in;
        _recycler = BufferRecycler.instance();
        _inputBuffer = _recycler.allocInputBuffer(LZFChunk.MAX_CHUNK_LEN);
        // let's not yet allocate encoding buffer; don't know optimal size
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
    // InputStream implementation
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
    public int read() throws IOException
    {
        if (!readyBuffer()) {
            return -1;
        }
        return _encodedBytes[_bufferPosition++] & 255;
    }
        
    @Override
    public int read(final byte[] buffer) throws IOException
    {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public int read(final byte[] buffer, int offset, int length) throws IOException
    {
        if (length < 1) {
            return 0;
        }
        if (!readyBuffer()) {
            return -1;
        }
        // First let's read however much data we happen to have...
        int chunkLength = Math.min(_bufferLength - _bufferPosition, length);
        System.arraycopy(_encodedBytes, _bufferPosition, buffer, offset, chunkLength);
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
            System.arraycopy(_encodedBytes, _bufferPosition, buffer, offset, chunkLength);
            _bufferPosition += chunkLength;
            totalRead += chunkLength;
        } while (totalRead < length);

        return totalRead;
    }
    
    @Override
    public void close() throws IOException
    {
        _bufferPosition = _bufferLength = 0;
        byte[] buf = _encodedBytes;
        if (buf != null) {
            _encodedBytes = null;
            _recycler.releaseEncodeBuffer(buf);
        }
        if (_encoder != null) {
            _encoder.close();
        }
        _closeInput();
    }
    
    private void _closeInput() throws IOException
    {
        byte[] buf = _inputBuffer;
        if (buf != null) {
            _inputBuffer = null;
            _recycler.releaseInputBuffer(buf);
        }
        if (!_inputStreamClosed) {
            _inputStreamClosed = true;
            _inputStream.close();
        }
    }

    /**
     * Overridden to just skip at most a single chunk at a time
     */
    @Override
    public long skip(long n) throws IOException
    {
        if (_inputStreamClosed) {
            return -1;
        }
        int left = (_bufferLength - _bufferPosition);
        // if none left, must read more:
        if (left <= 0) {
            // otherwise must read more to skip...
            int b = read();
            if (b < 0) { // EOF
                return -1;
            }
            // push it back to get accurate skip count
            --_bufferPosition;
            left = (_bufferLength - _bufferPosition);
        }
        // either way, just skip whatever we have decoded
        if (left > n) {
            left = (int) n;
        }
        _bufferPosition += left;
        return left;
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
        if (_bufferPosition < _bufferLength) {
            return true;
        }
        if (_inputStreamClosed) {
            return false;
        }
        // Ok: read as much as we can from input source first
        int count = _inputStream.read(_inputBuffer, 0, _inputBuffer.length);
        if (count < 0) { // if no input read, it's EOF
            _closeInput(); // and we can close input source as well
            return false;
        }
        int chunkLength = count;
        int left = _inputBuffer.length - count;
        
        while ((count = _inputStream.read(_inputBuffer, chunkLength, left)) > 0) {
            chunkLength += count;
            left -= count;
            if (left < 1) {
                break;
            }
        }

        _bufferPosition = 0;
        // Ok: if we don't yet have an encoder (and buffer for it), let's get one
        if (_encoder == null) {
            // need 7 byte header, plus regular max buffer size:
            int bufferLen = chunkLength + ((chunkLength + 31) >> 5) + 7;
            _encoder = ChunkEncoderFactory.optimalNonAllocatingInstance(bufferLen);
        }
        if (_encodedBytes == null) {
            int bufferLen = chunkLength + ((chunkLength + 31) >> 5) + 7;
            _encodedBytes = _recycler.allocEncodingBuffer(bufferLen);
        }
        // offset of 7 so we can prepend header as necessary
        int encodeEnd = _encoder.tryCompress(_inputBuffer, 0, chunkLength, _encodedBytes, 7);
        // but did it compress?
        if (encodeEnd < (chunkLength + 5)) { // yes! (compared to 5 byte uncomp prefix, data)
            // prepend header in situ
            LZFChunk.appendCompressedHeader(chunkLength, encodeEnd-7, _encodedBytes, 0);
            _bufferLength = encodeEnd;
        } else { // no -- so sad...
            int ptr = LZFChunk.appendNonCompressedHeader(chunkLength, _encodedBytes, 0);
            // TODO: figure out a way to avoid this copy; need a header
            System.arraycopy(_inputBuffer, 0, _encodedBytes, ptr, chunkLength);
            _bufferLength = ptr + chunkLength;
        }
        if (count < 0) { // did we get end-of-input?
            _closeInput();
        }
        return true;
    }
}
