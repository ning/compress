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

import com.ning.compress.BufferRecycler;
import com.ning.compress.DataHandler;
import com.ning.compress.Uncompressor;
import com.ning.compress.lzf.util.ChunkDecoderFactory;

/**
 * {@link com.ning.compress.Uncompressor} implementation for uncompressing
 * LZF encoded data in "push" mode, in which input is not
 * read using {@link java.io.InputStream} but rather pushed to
 * uncompressor in variable length chunks.
 */
public class LZFUncompressor extends Uncompressor
{
    /*
    ///////////////////////////////////////////////////////////////////////
    // State constants
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * State in which a new block or end-of-stream is expected.
     */
    protected final static int STATE_INITIAL = 0;

    protected final static int STATE_HEADER_Z_GOTTEN = 1;
    protected final static int STATE_HEADER_ZV_GOTTEN = 2;

    protected final static int STATE_HEADER_COMPRESSED_0 = 3;
    protected final static int STATE_HEADER_COMPRESSED_1 = 4;
    protected final static int STATE_HEADER_COMPRESSED_2 = 5;
    protected final static int STATE_HEADER_COMPRESSED_3 = 6;
    protected final static int STATE_HEADER_COMPRESSED_BUFFERING = 7;

    protected final static int STATE_HEADER_UNCOMPRESSED_0 = 8;
    protected final static int STATE_HEADER_UNCOMPRESSED_1 = 9;
    protected final static int STATE_HEADER_UNCOMPRESSED_STREAMING = 10;
    
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
     * Underlying decompressor we use for chunk decompression.
     */
    protected final ChunkDecoder _decoder;

    protected final BufferRecycler _recycler;
    
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
     * Flag set if {@link DataHandler} indicates that processing should be
     * terminated.
     */
    protected boolean _terminated;
    
    /**
     * Number of bytes in current, compressed block
     */
    protected int _compressedLength;

    /**
     * Number of bytes from current block, either after uncompressing data
     * (for compressed blocks), or included in stream (for uncompressed).
     */
    protected int _uncompressedLength;

    /**
     * Buffer in which compressed input is buffered if necessary, to get
     * full chunks for decoding.
     */
    protected byte[] _inputBuffer;

    /**
     * Buffer used for data uncompressed from <code>_inputBuffer</code>.
     */
    protected byte[] _decodeBuffer;
    
    /**
     * Number of bytes that have been buffered in {@link #_inputBuffer} to be
     * uncompressed; or copied directly from uncompressed block.
     */
    protected int _bytesReadFromBlock;
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Instance creation
    ///////////////////////////////////////////////////////////////////////
     */
    
    public LZFUncompressor(DataHandler handler) {
        this(handler, ChunkDecoderFactory.optimalInstance());
    }
    
    public LZFUncompressor(DataHandler handler, ChunkDecoder dec)
    {
        _handler = handler;
        _decoder = dec;
        _recycler = BufferRecycler.instance();
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Uncompressor API implementation
    ///////////////////////////////////////////////////////////////////////
     */
    
    @Override
    public boolean feedCompressedData(byte[] comp, int offset, int len) throws IOException
    {
        final int end = offset + len;
        
        while (offset < end) {
            byte b = comp[offset++];

            switch (_state) {
            case STATE_INITIAL:
                if (b != LZFChunk.BYTE_Z) {
                    _reportBadHeader(comp, offset, len, 0);
                }
                if (offset >= end) {
                    _state = STATE_HEADER_Z_GOTTEN;
                    break;
                }
                b = comp[offset++];
                // fall through
            case STATE_HEADER_Z_GOTTEN:
                if (b != LZFChunk.BYTE_V) {
                    _reportBadHeader(comp, offset, len, 1);
                }
                if (offset >= end) {
                    _state = STATE_HEADER_ZV_GOTTEN;
                    break;
                }
                b = comp[offset++];
                // fall through
            case STATE_HEADER_ZV_GOTTEN:
                _bytesReadFromBlock = 0;
                {
                    int type = b & 0xFF;
                    if (type != LZFChunk.BLOCK_TYPE_COMPRESSED) {
                        if (type == LZFChunk.BLOCK_TYPE_NON_COMPRESSED) {
                            _state = STATE_HEADER_UNCOMPRESSED_0;
                            continue;
                        }
                        _reportBadBlockType(comp, offset, len, type);
                    }
                }
                _state = STATE_HEADER_COMPRESSED_0;
                if (offset >= end) {
                    break;
                }
                b = comp[offset++];
                // fall through for compressed blocks
            case STATE_HEADER_COMPRESSED_0: // first byte of compressed-length
                _compressedLength = b & 0xFF;
                if (offset >= end) {
                    _state = STATE_HEADER_COMPRESSED_1;
                    break;
                }
                b = comp[offset++];
                // fall through
            case STATE_HEADER_COMPRESSED_1:
                _compressedLength = (_compressedLength << 8) + (b & 0xFF);
                if (offset >= end) {
                    _state = STATE_HEADER_COMPRESSED_2;
                    break;
                }
                b = comp[offset++];
                // fall through
            case STATE_HEADER_COMPRESSED_2:
                _uncompressedLength = b & 0xFF;
                if (offset >= end) {
                    _state = STATE_HEADER_COMPRESSED_3;
                    break;
                }
                b = comp[offset++];
                // fall through
            case STATE_HEADER_COMPRESSED_3:
                _uncompressedLength = (_uncompressedLength << 8) + (b & 0xFF);
                _state = STATE_HEADER_COMPRESSED_BUFFERING;
                if (offset >= end) {
                    break;
                }
                b = comp[offset++];
                // fall through
            case STATE_HEADER_COMPRESSED_BUFFERING:
                offset = _handleCompressed(comp, --offset, end);
                // either state changes, or we run out of input...
                break;

            case STATE_HEADER_UNCOMPRESSED_0:
                _uncompressedLength = b & 0xFF;
                if (offset >= end) {
                    _state = STATE_HEADER_UNCOMPRESSED_1;
                    break;
                }
                b = comp[offset++];
                // fall through
            case STATE_HEADER_UNCOMPRESSED_1:
                _uncompressedLength = (_uncompressedLength << 8) + (b & 0xFF);
                _state = STATE_HEADER_UNCOMPRESSED_STREAMING;
                if (offset >= end) {
                    break;
                }
                b = comp[offset++];
                // fall through
            case STATE_HEADER_UNCOMPRESSED_STREAMING:
                offset = _handleUncompressed(comp, --offset, end);
                if (_terminated) {
                    break;
                }
                // All done?
                if (_bytesReadFromBlock == _uncompressedLength) {
                    _state = STATE_INITIAL;
                }
                break;
            }
        }
        return !_terminated;
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
        // 24-May-2012, tatu: Should we call this here; or fail with exception?
        _handler.allDataHandled();
        if (!_terminated) {
            if (_state != STATE_INITIAL) {
                if (_state == STATE_HEADER_COMPRESSED_BUFFERING) {
                    throw new LZFException("Incomplete compressed LZF block; only got "+_bytesReadFromBlock
                            +" bytes, needed "+_compressedLength);
                }
                if (_state == STATE_HEADER_UNCOMPRESSED_STREAMING) {
                    throw new LZFException("Incomplete uncompressed LZF block; only got "+_bytesReadFromBlock
                            +" bytes, needed "+_uncompressedLength);
                }
                throw new LZFException("Incomplete LZF block; decoding state = "+_state);
            }
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods, decompression
    ///////////////////////////////////////////////////////////////////////
     */

    private final int _handleUncompressed(byte[] comp, int offset, int end) throws IOException
    {
        // Simple, we just do pass through...
        int amount = Math.min(end-offset, _uncompressedLength-_bytesReadFromBlock);
        if (!_handler.handleData(comp, offset, amount)) {
            _terminated = true;
        }
        _bytesReadFromBlock += amount;
        return offset + amount;
    }

    private final int _handleCompressed(byte[] comp, int offset, int end) throws IOException
    {
        // One special case: if we get the whole block, can avoid buffering:
        int available = end-offset;
        if (_bytesReadFromBlock == 0 && available >= _compressedLength) {
            _uncompress(comp, offset, _compressedLength);
            offset += _compressedLength;
            _state = STATE_INITIAL;
            return offset;
        }
        // otherwise need to buffer
        if (_inputBuffer == null) {
            _inputBuffer = _recycler.allocInputBuffer(LZFChunk.MAX_CHUNK_LEN);
        }
        int amount = Math.min(available, _compressedLength - _bytesReadFromBlock);
        System.arraycopy(comp, offset, _inputBuffer, _bytesReadFromBlock, amount);
        offset += amount;
        _bytesReadFromBlock += amount;
        // Got it all?
        if (_bytesReadFromBlock == _compressedLength) {
            _uncompress(_inputBuffer, 0, _compressedLength);
            _state = STATE_INITIAL;
        }
        return offset;
    }

    private final void _uncompress(byte[] src, int srcOffset, int len) throws IOException
    {
        if (_decodeBuffer == null) {
            _decodeBuffer = _recycler.allocDecodeBuffer(LZFChunk.MAX_CHUNK_LEN);
        }
        _decoder.decodeChunk(src, srcOffset, _decodeBuffer, 0, _uncompressedLength);
        _handler.handleData(_decodeBuffer, 0, _uncompressedLength);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods, error reporting
    ///////////////////////////////////////////////////////////////////////
     */

    protected void _reportBadHeader(byte[] comp, int nextOffset, int len, int relative)
            throws IOException
    {
        char exp = (relative == 0) ? 'Z' : 'V';
        --nextOffset;
        throw new LZFException("Bad block: byte #"+relative+" of block header not '"
                +exp+"' (0x"+Integer.toHexString(exp)
                +") but 0x"+Integer.toHexString(comp[nextOffset] & 0xFF)
                +" (at "+(nextOffset-1)+"/"+(len)+")");
    }

    protected void _reportBadBlockType(byte[] comp, int nextOffset, int len, int type)
            throws IOException
    {
        throw new LZFException("Bad block: unrecognized type 0x"+Integer.toHexString(type & 0xFF)
                +" (at "+(nextOffset-1)+"/"+len+")");
    }
}
