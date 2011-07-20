package com.ning.compress.lzf.util;

import java.io.File;

import org.testng.annotations.Test;
import org.testng.Assert;

public class TestFileStreams
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
}
