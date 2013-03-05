package com.ning.compress.lzf;

import java.io.*;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.compress.BaseForTests;

public class TestLZFCompressingInputStream extends BaseForTests
{
    @Test
    public void testSimpleCompression() throws IOException
    {
        // produce multiple chunks, about 3 here:
        byte[] source = constructFluff(140000);
        LZFCompressingInputStream compIn = new LZFCompressingInputStream(new ByteArrayInputStream(source));
        byte[] comp = readAll(compIn);
        byte[] uncomp = uncompress(comp);
        Assert.assertEquals(uncomp, source);

        // and then check that size is about same as with static methods
        byte[] comp2 = compress(source);
        Assert.assertEquals(comp2.length, comp.length);
    }

    @Test
    public void testSimpleNonCompressed() throws IOException
    {
        // produce two chunks as well
        byte[] source = this.constructUncompressable(89000);
        LZFCompressingInputStream compIn = new LZFCompressingInputStream(new ByteArrayInputStream(source));
        byte[] comp = readAll(compIn);
        // 2 non-compressed chunks with headers:
        Assert.assertEquals(comp.length, 89000 + 5 + 5);
        byte[] uncomp = uncompress(comp);
        Assert.assertEquals(uncomp, source);
    }
}
