package com.ning.compress.gzip;

import java.io.IOException;
//import java.util.zip.Inflater;

import com.ning.compress.BufferRecycler;
import com.ning.compress.DataHandler;
import com.ning.compress.Uncompressor;
import com.ning.compress.lzf.LZFChunk;

public class GZIPUncompressor extends Uncompressor
{
    protected final DataHandler _dataHandler;

    /**
     * Object that handles details of buffer recycling
     */
    protected final BufferRecycler _recycler;

    protected byte[] _decodeBuffer;
    
    public GZIPUncompressor(DataHandler h)
    {
        _dataHandler = h;
        _recycler = BufferRecycler.instance();
        _decodeBuffer = _recycler.allocInputBuffer(LZFChunk.MAX_CHUNK_LEN);
    }
    
    @Override
    public void feedCompressedData(byte[] comp, int offset, int len) throws IOException
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void complete() throws IOException
    {
        // TODO Auto-generated method stub
    }
}
