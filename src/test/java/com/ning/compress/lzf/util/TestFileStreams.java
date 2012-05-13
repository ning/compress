package com.ning.compress.lzf.util;

import java.io.*;

import org.testng.annotations.Test;
import org.testng.Assert;

import com.ning.compress.BaseForTests;

public class TestFileStreams extends BaseForTests
{
    @Test 
    public void testStreams() throws Exception
    {
        File f = File.createTempFile("lzf-test", ".lzf");
        f.deleteOnExit();

        // First, write encoded stuff (won't compress, but produces something)
        byte[] input = "Whatever stuff...".getBytes("UTF-8");

        LZFFileOutputStream out = new LZFFileOutputStream(f);
        out.write(input);
        out.close();

        long len = f.length();
        // happens to be 22; 17 bytes uncompressed, with 5 byte header
        Assert.assertEquals(len, 22L);

        LZFFileInputStream in = new LZFFileInputStream(f);
        for (int i = 0; i < input.length; ++i) {
            Assert.assertEquals(in.read(), input[i] & 0xFF);
        }
        Assert.assertEquals(in.read(), -1);
        in.close();
    }

    @Test 
    public void testReadAndWrite() throws Exception
    {
        File f = File.createTempFile("lzf-test", ".lzf");
        f.deleteOnExit();

        byte[] fluff = constructFluff(132000);
        LZFFileOutputStream fout = new LZFFileOutputStream(f);
        fout.write(fluff);
        fout.close();
        
        LZFFileInputStream in = new LZFFileInputStream(f);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(fluff.length);
        in.readAndWrite(bytes);
        in.close();
        byte[] actual = bytes.toByteArray();
        Assert.assertEquals(actual.length, fluff.length);
        Assert.assertEquals(actual, fluff);
    }
}
