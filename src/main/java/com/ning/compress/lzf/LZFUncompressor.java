package com.ning.compress.lzf;

import java.io.IOException;

import com.ning.compress.Uncompressor;
import com.ning.compress.lzf.util.ChunkDecoderFactory;

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
    protected final static int STATE_HEADER_UNCOMPRESSED_STREAMING = 9;
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Configuration, helper objects
    ///////////////////////////////////////////////////////////////////////
     */
    
    protected final ChunkDecoder _decoder;

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
     * Number of bytes in current, compressed block
     */
    protected int _compressedLength;

    /**
     * Number of bytes from current block, either after uncompressing data
     * (for compressed blocks), or included in stream (for uncompressed).
     */
    protected int _uncompressedLength;

    /**
     * Number of bytes left to read for the current block.
     */
    protected int _bytesLeftInBlock;
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Instance creation
    ///////////////////////////////////////////////////////////////////////
     */
    
    public LZFUncompressor() {
        this(ChunkDecoderFactory.optimalInstance());
    }
    
    public LZFUncompressor(ChunkDecoder dec)
    {
        _decoder = dec;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Uncompressor impl
    ///////////////////////////////////////////////////////////////////////
     */
    
    @Override
    public void feedCompressedData(byte[] comp, int offset, int len) throws IOException
    {
        // !!! TODO
    }

    @Override
    public void complete() throws IOException
    {
        // !!! TODO
    }
}
