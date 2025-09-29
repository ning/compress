package com.ning.compress.lzf;

import java.io.*;

import com.ning.compress.BaseForTests;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertArrayEquals(source, uncomp);

        // and then check that size is about same as with static methods
        byte[] comp2 = compress(source);
        assertEquals(comp.length, comp2.length);
    }

    @Test
    public void testSimpleNonCompressed() throws IOException
    {
        // produce two chunks as well
        byte[] source = this.constructUncompressable(89000);
        LZFCompressingInputStream compIn = new LZFCompressingInputStream(new ByteArrayInputStream(source));
        byte[] comp = readAll(compIn);
        // 2 non-compressed chunks with headers:
        assertEquals(89000 + 5 + 5, comp.length);
        byte[] uncomp = uncompress(comp);
        assertArrayEquals(source, uncomp);
    }
}
