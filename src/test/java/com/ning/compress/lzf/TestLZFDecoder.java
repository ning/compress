package com.ning.compress.lzf;

import java.io.*;
import java.nio.charset.StandardCharsets;

import com.ning.compress.BaseForTests;
import com.ning.compress.lzf.impl.UnsafeChunkDecoder;
import com.ning.compress.lzf.util.ChunkDecoderFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    public void testUnsafeValidation() {
        UnsafeChunkDecoder decoder = new UnsafeChunkDecoder();

        byte[] array = new byte[10];
        int goodStart = 2;
        int goodEnd = 5;
        assertThrows(NullPointerException.class, () -> decoder.decodeChunk(null, goodStart, array, goodStart, goodEnd));
        assertThrows(NullPointerException.class, () -> decoder.decodeChunk(array, goodStart, null, goodStart, goodEnd));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, -1, array, goodStart, goodEnd));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, 12, array, goodStart, goodEnd));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, goodStart, array, -1, goodEnd));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, goodStart, array, goodStart, 1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, goodStart, array, goodStart, 12));
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Second-level test methods
    ///////////////////////////////////////////////////////////////////////
     */

    private void _testSimple(ChunkDecoder decoder) throws IOException
    {
        byte[] orig = "Another trivial test".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = compress(orig);
        byte[] result = decoder.decode(compressed);
        assertArrayEquals(orig, result);

        // also, ensure that offset, length are passed
        byte[] compressed2 = new byte[compressed.length + 4];
        System.arraycopy(compressed, 0, compressed2, 2, compressed.length);

        result = decoder.decode(compressed2, 2, compressed.length);
        assertArrayEquals(orig, result);

        // two ways to do that as well:
        result = LZFDecoder.decode(compressed2, 2, compressed.length);
        assertArrayEquals(orig, result);
    }
    
    private void _testLonger(ChunkDecoder decoder) throws IOException
    {
        byte[] orig = this.constructFluff(250000); // 250k
        byte[] compressed = compress(orig);
        byte[] result = decoder.decode(compressed);
        assertArrayEquals(orig, result);

        // also, ensure that offset, length are passed
        byte[] compressed2 = new byte[compressed.length + 4];
        System.arraycopy(compressed, 0, compressed2, 2, compressed.length);

        result = decoder.decode(compressed2, 2, compressed.length);
        assertArrayEquals(orig, result);

        // two ways to do that as well:
        result = LZFDecoder.decode(compressed2, 2, compressed.length);
        assertArrayEquals(orig, result);
    }

    private void _testChunks(ChunkDecoder decoder) throws IOException
    {
        byte[] orig1 = "Another trivial test".getBytes(StandardCharsets.UTF_8);
        byte[] orig2 = " with some of repepepepepetitition too!".getBytes(StandardCharsets.UTF_8);
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
        assertArrayEquals(orig, result);
   }
}
