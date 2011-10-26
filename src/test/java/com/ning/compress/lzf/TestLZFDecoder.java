package com.ning.compress.lzf;

import java.io.*;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.compress.lzf.util.ChunkDecoderFactory;

public class TestLZFDecoder
{
    @Test
    public void testSimple() throws IOException
    {
        byte[] orig = "Another trivial test".getBytes("UTF-8");
        byte[] compressed = LZFEncoder.encode(orig);
        byte[] result = ChunkDecoderFactory.optimalInstance().decode(compressed);
        Assert.assertEquals(result, orig);
   }

    @Test
    public void testChunks() throws IOException
    {
        byte[] orig1 = "Another trivial test".getBytes("UTF-8");
        byte[] orig2 = " with some of repepepepepetitition too!".getBytes("UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(orig1);
        out.write(orig2);
        byte[] orig = out.toByteArray();

        byte[] compressed1 = LZFEncoder.encode(orig1);
        byte[] compressed2 = LZFEncoder.encode(orig2);
        out = new ByteArrayOutputStream();
        out.write(compressed1);
        out.write(compressed2);
        byte[] compressed = out.toByteArray();
        
        byte[] result = ChunkDecoderFactory.optimalInstance().decode(compressed);
        Assert.assertEquals(result, orig);
   }
}
