package com.ning.compress.lzf;

import java.io.*;
import java.util.Arrays;

import com.ning.compress.BaseForTests;
import com.ning.compress.lzf.impl.UnsafeChunkEncoder;
import com.ning.compress.lzf.impl.UnsafeChunkEncoderBE;
import com.ning.compress.lzf.impl.UnsafeChunkEncoderLE;
import com.ning.compress.lzf.util.ChunkEncoderFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LZFEncoderTest extends BaseForTests
{
    @Test
    public void testBigSizeEstimate()
    {
        for (int amt : new int[] {
                100, 250, 600,
                10000, 50000, 65000, 120000, 130000,
                3 * 0x10000 + 4,
                15 * 0x10000 + 4,
                1000 * 0x10000 + 4,
        }) {
            int estimate = LZFEncoder.estimateMaxWorkspaceSize(amt);
            int chunks = ((amt + 0xFFFE) / 0xFFFF);
            int expMin = 2 + amt + (chunks * 5); // 5-byte header for uncompressed; however, not enough workspace
            int expMax = ((int) (0.05 * 0xFFFF)) + amt + (chunks * 7);
            if (estimate < expMin || estimate > expMax) {
                fail("Expected ratio for "+amt+" to be "+expMin+" <= x <= "+expMax+", was: "+estimate);
            }
//System.err.printf("%d < %d < %d\n", expMin, estimate, expMax);
        }
    }

    // as per [compress-lzf#43]
    @Test
    public void testSmallSizeEstimate()
    {
        // and here we ensure that specific uncompressable case won't fail
        byte[] in = new byte[] {0, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0, 4, 0, 0, 0};
        int outSize = LZFEncoder.estimateMaxWorkspaceSize(in.length);
        LZFEncoder.appendEncoded(in, 0, in.length, new byte[outSize], 0);
    }

    @Test
    public void testCompressableChunksSingle() throws Exception
    {
        byte[] source = constructFluff(55000);
        _testCompressableChunksSingle(source, ChunkEncoderFactory.safeInstance());
        _testCompressableChunksSingle(source, ChunkEncoderFactory.optimalInstance());
    }

    private void _testCompressableChunksSingle(byte[] source, ChunkEncoder encoder) throws Exception
    {
        byte[] buffer = new byte[LZFEncoder.estimateMaxWorkspaceSize(source.length)];
        int compLen = LZFEncoder.appendEncoded(encoder, source, 0, source.length, buffer, 0);

        // and make sure we get identical compression
        byte[] bufferAsBlock = Arrays.copyOf(buffer, compLen);
        byte[] asBlockStd = LZFEncoder.encode(source);
        assertArrayEquals(bufferAsBlock, asBlockStd);

        // then uncompress, verify
        byte[] uncomp = uncompress(buffer, 0, compLen);

        assertArrayEquals(source, uncomp);
    }

    @Test
    public void testCompressableChunksMulti() throws Exception
    {
        // let's do bit over 256k, to get multiple chunks
        byte[] source = constructFluff(4 * 0xFFFF + 4000);
        _testCompressableChunksMulti(source, ChunkEncoderFactory.safeInstance());
        _testCompressableChunksMulti(source, ChunkEncoderFactory.optimalInstance());
    }
    
    private void _testCompressableChunksMulti(byte[] source, ChunkEncoder encoder) throws Exception
    {
        byte[] buffer = new byte[LZFEncoder.estimateMaxWorkspaceSize(source.length)];
        int compLen = LZFEncoder.appendEncoded(encoder, source, 0, source.length, buffer, 0);

        // and make sure we get identical compression
        byte[] bufferAsBlock = Arrays.copyOf(buffer, compLen);
        byte[] asBlockStd = LZFEncoder.encode(encoder, source, 0, source.length);
        assertArrayEquals(bufferAsBlock, asBlockStd);

        // then uncompress, verify
        byte[] uncomp = uncompress(buffer, 0, compLen);

        assertArrayEquals(source, uncomp);
    }

    @Test
    public void testNonCompressableChunksSingle() throws Exception
    {
        byte[] source = constructUncompressable(4000);
        _testNonCompressableChunksSingle(source, ChunkEncoderFactory.safeInstance());
        _testNonCompressableChunksSingle(source, ChunkEncoderFactory.optimalInstance());
    }
    
    private void _testNonCompressableChunksSingle(byte[] source, ChunkEncoder encoder) throws Exception
    {
        byte[] buffer = new byte[LZFEncoder.estimateMaxWorkspaceSize(source.length)];
        int compLen = LZFEncoder.appendEncoded(source, 0, source.length, buffer, 0);
        
        // and make sure we get identical compression
        byte[] bufferAsBlock = Arrays.copyOf(buffer, compLen);
        byte[] asBlockStd = LZFEncoder.encode(encoder, source, 0, source.length);
        assertArrayEquals(bufferAsBlock, asBlockStd);

        // then uncompress, verify
        byte[] uncomp = uncompress(buffer, 0, compLen);

        assertArrayEquals(source, uncomp);
    }

    @Test
    public void testConditionalCompression() throws Exception
    {
        final byte[] input = constructFluff(52000);
        
        _testConditionalCompression(ChunkEncoderFactory.safeInstance(), input);
        _testConditionalCompression(ChunkEncoderFactory.optimalInstance(), input);
    }

    private void _testConditionalCompression(ChunkEncoder enc, final byte[] input) throws IOException
    {
        // double-check expected compression ratio
        byte[] comp = enc.encodeChunk(input, 0, input.length).getData();
        int pct = (int) (100.0 * comp.length / input.length);
        // happens to compress to about 61%, good
        assertEquals(61, pct);

        // should be ok if we only require down to 70% compression
        byte[] buf = new byte[60000];
        int offset = enc.appendEncodedIfCompresses(input, 0.70, 0, input.length, buf, 0);
        assertEquals(comp.length, offset);

        // but not to 60%
        offset = enc.appendEncodedIfCompresses(input, 0.60, 0, input.length, buf, 0);
        assertEquals(-1, offset);

        // // // Second part: OutputStream alternatives
        
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(60000);
        assertTrue(enc.encodeAndWriteChunkIfCompresses(input, 0, input.length, bytes, 0.70));
        assertEquals(comp.length, bytes.size());
        byte[] output = bytes.toByteArray();
        assertArrayEquals(comp, output);

        bytes = new ByteArrayOutputStream(60000);
        assertFalse(enc.encodeAndWriteChunkIfCompresses(input, 0, input.length, bytes, 0.60));
        assertEquals(0, bytes.size());

        // // // Third part: chunk creation

        LZFChunk chunk = enc.encodeChunkIfCompresses(input, 0, input.length, 0.70);
        assertNotNull(chunk);
        assertEquals(comp.length, chunk.length());
        assertArrayEquals(comp, chunk.getData());

        chunk = enc.encodeChunkIfCompresses(input, 0, input.length, 0.60);
        assertNull(chunk);
    }

    @Test
    public void testUnsafeValidation() {
        _testUnsafeValidation(new UnsafeChunkEncoderBE(10));
        _testUnsafeValidation(new UnsafeChunkEncoderLE(10));

    }

    private void _testUnsafeValidation(UnsafeChunkEncoder encoder) {
        byte[] array = new byte[10];
        int goodStart = 2;
        int goodEnd = 5;

        assertThrows(NullPointerException.class, () -> encoder.tryCompress(null, goodStart, goodEnd, array, goodStart));
        assertThrows(NullPointerException.class, () -> encoder.tryCompress(array, goodStart, goodEnd, null, goodStart));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> encoder.tryCompress(array, -1, goodEnd, array, goodStart));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> encoder.tryCompress(array, 12, goodEnd, array, goodStart));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> encoder.tryCompress(array, goodStart, 1, array, goodStart));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> encoder.tryCompress(array, goodStart, 12, array, goodStart));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> encoder.tryCompress(array, goodStart, goodEnd, array, -1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> encoder.tryCompress(array, goodStart, goodEnd, array, 12));
    }
}
