package com.ning.compress.lzf;

import java.io.*;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.compress.BaseForTests;
import com.ning.compress.lzf.util.ChunkDecoderFactory;

public class TestLZFDecoder extends BaseForTests
{
    @Test
    public void testSimple() throws IOException {
        _testSimple(ChunkDecoderFactory.safeInstance());
        _testSimple(ChunkDecoderFactory.optimalInstance());
    }

    @Test
    public void testLonger() throws IOException {
        _testLonger(ChunkDecoderFactory.safeInstance());
        _testLonger(ChunkDecoderFactory.optimalInstance());
    }
    
    @Test
    public void testChunks() throws IOException {
        _testChunks(ChunkDecoderFactory.safeInstance());
        _testChunks(ChunkDecoderFactory.optimalInstance());
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Second-level test methods
    ///////////////////////////////////////////////////////////////////////
     */

    private void _testSimple(ChunkDecoder decoder) throws IOException
    {
        byte[] orig = "Another trivial test".getBytes("UTF-8");
        byte[] compressed = compress(orig);
        byte[] result = decoder.decode(compressed);
        Assert.assertEquals(result, orig);

        // also, ensure that offset, length are passed
        byte[] compressed2 = new byte[compressed.length + 4];
        System.arraycopy(compressed, 0, compressed2, 2, compressed.length);

        result = decoder.decode(compressed2, 2, compressed.length);
        Assert.assertEquals(result, orig);

        // two ways to do that as well:
        result = LZFDecoder.decode(compressed2, 2, compressed.length);
        Assert.assertEquals(result, orig);
    }
    
    private void _testLonger(ChunkDecoder decoder) throws IOException
    {
        byte[] orig = this.constructFluff(250000); // 250k
        byte[] compressed = compress(orig);
        byte[] result = decoder.decode(compressed);
        Assert.assertEquals(result, orig);

        // also, ensure that offset, length are passed
        byte[] compressed2 = new byte[compressed.length + 4];
        System.arraycopy(compressed, 0, compressed2, 2, compressed.length);

        result = decoder.decode(compressed2, 2, compressed.length);
        Assert.assertEquals(result, orig);

        // two ways to do that as well:
        result = LZFDecoder.decode(compressed2, 2, compressed.length);
        Assert.assertEquals(result, orig);
    }

    private void _testChunks(ChunkDecoder decoder) throws IOException
    {
        byte[] orig1 = "Another trivial test".getBytes("UTF-8");
        byte[] orig2 = " with some of repepepepepetitition too!".getBytes("UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(orig1);
        out.write(orig2);
        byte[] orig = out.toByteArray();

        byte[] compressed1 = compress(orig1);
        byte[] compressed2 = compress(orig2);
        out = new ByteArrayOutputStream();
        out.write(compressed1);
        out.write(compressed2);
        byte[] compressed = out.toByteArray();
        
        byte[] result = decoder.decode(compressed);
        Assert.assertEquals(result, orig);
   }
}
