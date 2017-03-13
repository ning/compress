package com.ning.compress.gzip;

import java.io.*;
import java.util.Random;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.compress.BaseForTests;
import com.ning.compress.DataHandler;
import com.ning.compress.UncompressorOutputStream;

public class TestGzipUncompressor extends BaseForTests
{
    @Test 
    public void testSimpleSmall1by1() throws IOException
    {
        byte[] fluff = constructFluff(4000);
        byte[] comp = gzipAll(fluff);
        
        Collector co = new Collector();
        GZIPUncompressor uncomp = new GZIPUncompressor(co);
        for (int i = 0, end = comp.length; i < end; ++i) {
            uncomp.feedCompressedData(comp, i, 1);
        }
        uncomp.complete();
        byte[] result = co.getBytes();
        
        Assert.assertEquals(fluff, result);
    }

    @Test 
    public void testSimpleSmallAsChunk() throws IOException
    {
        byte[] fluff = constructFluff(4000);
        byte[] comp = gzipAll(fluff);

        // and then uncompress, first byte by bytes
        Collector co = new Collector();
        GZIPUncompressor uncomp = new GZIPUncompressor(co);
        uncomp.feedCompressedData(comp, 0, comp.length);
        uncomp.complete();
        byte[] result = co.getBytes();
        Assert.assertEquals(fluff, result);
    }
    
    @Test 
    public void testSimpleBiggerVarLength() throws IOException
    {
        byte[] fluff = constructFluff(190000);
        byte[] comp = gzipAll(fluff);

        // and then uncompress with arbitrary-sized blocks...
        Random rnd = new Random(123);
        Collector co = new Collector();
        GZIPUncompressor uncomp = new GZIPUncompressor(co);
        for (int i = 0, end = comp.length; i < end; ) {
            int size = Math.min(end-i, 1+rnd.nextInt(7));
            uncomp.feedCompressedData(comp, i, size);
            i += size;
        }
        uncomp.complete();
        byte[] result = co.getBytes();
        
        Assert.assertEquals(fluff, result);
    }

    @Test
    public void testSimpleBiggerOneChunk() throws IOException
    {
        byte[] fluff = constructFluff(275000);
        byte[] comp = gzipAll(fluff);

        // and then uncompress in one chunk
        Collector co = new Collector();
        GZIPUncompressor uncomp = new GZIPUncompressor(co);
        uncomp.feedCompressedData(comp, 0, comp.length);
        uncomp.complete();
        byte[] result = co.getBytes();
        
        Assert.assertEquals(fluff, result);
    }

    @Test
    public void testSimpleBiggerAsStream() throws IOException
    {
        byte[] fluff = constructFluff(277000);
        byte[] comp = gzipAll(fluff);
        Collector co = new Collector();
        UncompressorOutputStream out = new UncompressorOutputStream(new GZIPUncompressor(co));
        out.write(comp, 0, comp.length);
        out.close();
        byte[] result = co.getBytes();
        
        Assert.assertEquals(fluff, result);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
     */
    
    private byte[] gzipAll(byte[] input) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(16 + input.length>>2);
        OptimizedGZIPOutputStream gz = new OptimizedGZIPOutputStream(bytes);
        gz.write(input);
        gz.close();
        return bytes.toByteArray();
    }
    
    private final static class Collector implements DataHandler
    {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public boolean handleData(byte[] buffer, int offset, int len) throws IOException {
            bytes.write(buffer, offset, len);
            return true;
        }
        @Override
        public void allDataHandled() throws IOException { }
        public byte[] getBytes() { return bytes.toByteArray(); }
    }
}
