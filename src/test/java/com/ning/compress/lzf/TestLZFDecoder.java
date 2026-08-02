package com.ning.compress.lzf;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.ning.compress.BaseForTests;
import com.ning.compress.lzf.impl.UnsafeChunkDecoder;
import com.ning.compress.lzf.util.ChunkDecoderFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertThrows(NullPointerException.class, () -> decoder.decodeChunk(null, goodStart, goodEnd, array, goodStart, goodEnd));
        assertThrows(NullPointerException.class, () -> decoder.decodeChunk(array, goodStart, goodEnd, null, goodStart, goodEnd));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, -1, goodEnd, array, goodStart, goodEnd));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, goodStart, goodStart - 1, array, goodStart, goodEnd));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, goodStart, -1, array, goodStart, goodEnd));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, goodStart, array.length + 1, array, goodStart, goodEnd));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, goodStart, goodEnd, array, -1, goodEnd));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, goodStart, goodEnd, array, goodStart, goodStart - 1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, goodStart, goodEnd, array, goodStart, -1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> decoder.decodeChunk(array, goodStart, goodEnd, array, goodStart, array.length + 1));
    }

    @Test
    public void testTruncatedShortBackReference() {
        // Payload (after the 7 byte header) is: `0x00` = literal run of 1 byte (`0x04`),
        // followed by `0x50` = short back-reference, which needs one more byte for its
        // offset -- but the chunk ends. First case has a trailing byte that is outside of
        // the given input end (and must not be read), second case has no trailing byte at all.
        byte[] inputWithTrailingByte = new byte[] {
                LZFChunk.BYTE_Z, LZFChunk.BYTE_V, LZFChunk.BLOCK_TYPE_COMPRESSED,
                0, 3, 0, (byte) 0x8f, 0, 4, 0x50, 0x53
        };
        byte[] truncatedInput = new byte[] {
                LZFChunk.BYTE_Z, LZFChunk.BYTE_V, LZFChunk.BLOCK_TYPE_COMPRESSED,
                0, 3, 0, (byte) 0x8f, 0, 4, 0x50
        };

        assertThrows(LZFException.class, () ->
                ChunkDecoderFactory.safeInstance().decodeChunk(inputWithTrailingByte, 7, 10, new byte[143], 0, 143));
        assertThrows(LZFException.class, () ->
                ChunkDecoderFactory.optimalInstance().decodeChunk(inputWithTrailingByte, 7, 10, new byte[143], 0, 143));
        assertThrows(LZFException.class, () ->
                ChunkDecoderFactory.safeInstance().decodeChunk(truncatedInput, 7, 10, new byte[143], 0, 143));
        assertThrows(LZFException.class, () ->
                ChunkDecoderFactory.optimalInstance().decodeChunk(truncatedInput, 7, 10, new byte[143], 0, 143));
    }

    @Test
    public void testTruncatedBlocks() {
        // Note: `decode(byte[])` validates framing up front, via `calculateUncompressedSize()`.
        // These go through the overload that takes a target buffer, which does not -- so the
        // framing loop itself has to check that header and content fit within the input.
        byte[][] truncatedInputs = new byte[][] {
                // signature only: no type, no length
                { LZFChunk.BYTE_Z, LZFChunk.BYTE_V },
                // ... type but no length
                { LZFChunk.BYTE_Z, LZFChunk.BYTE_V, LZFChunk.BLOCK_TYPE_COMPRESSED },
                // ... only one of the two length bytes
                { LZFChunk.BYTE_Z, LZFChunk.BYTE_V, LZFChunk.BLOCK_TYPE_COMPRESSED, 0 },
                // compressed block without the 2 bytes of uncompressed length
                { LZFChunk.BYTE_Z, LZFChunk.BYTE_V, LZFChunk.BLOCK_TYPE_COMPRESSED, 0, 2, 0 },
                // compressed block declaring 2 bytes of content, but only 1 included
                { LZFChunk.BYTE_Z, LZFChunk.BYTE_V, LZFChunk.BLOCK_TYPE_COMPRESSED, 0, 2, 0, 0, 0 },
                // non-compressed block declaring 10 bytes of content, but only 2 included
                { LZFChunk.BYTE_Z, LZFChunk.BYTE_V, LZFChunk.BLOCK_TYPE_NON_COMPRESSED, 0, 10, 0x41, 0x42 },
        };

        for (int i = 0; i < truncatedInputs.length; ++i) {
            final byte[] input = truncatedInputs[i];
            String desc = "truncated input #"+i+" (length "+input.length+")";
            assertThrows(LZFException.class, () ->
                    ChunkDecoderFactory.safeInstance().decode(input, 0, input.length, new byte[64]), desc);
            assertThrows(LZFException.class, () ->
                    ChunkDecoderFactory.optimalInstance().decode(input, 0, input.length, new byte[64]), desc);
        }
    }

    @Test
    public void testBackRefBeforeChunkStart() {
        _testBackRefBeforeChunkStart(ChunkDecoderFactory.safeInstance());
        _testBackRefBeforeChunkStart(ChunkDecoderFactory.optimalInstance());
    }

    @Test
    public void testBackRefPastChunkEnd() {
        _testBackRefPastChunkEnd(ChunkDecoderFactory.safeInstance());
        _testBackRefPastChunkEnd(ChunkDecoderFactory.optimalInstance());
    }

    @Test
    public void testBackRefIntoPreviousChunk() throws IOException {
        // First chunk decodes normally; second one consists of nothing but a back-reference
        // with offset -1, which would have to reach into output of the first chunk
        byte[] firstChunk = compress("SECRETSECRETSECRETSECRET".getBytes(StandardCharsets.UTF_8));
        byte[] secondChunk = new byte[] {
                LZFChunk.BYTE_Z, LZFChunk.BYTE_V, LZFChunk.BLOCK_TYPE_COMPRESSED,
                0, 2, // compressed length
                0, 3, // uncompressed length
                SHORT_BACK_REF_MINUS_ONE[0], SHORT_BACK_REF_MINUS_ONE[1]
        };
        byte[] input = new byte[firstChunk.length + secondChunk.length];
        System.arraycopy(firstChunk, 0, input, 0, firstChunk.length);
        System.arraycopy(secondChunk, 0, input, firstChunk.length, secondChunk.length);

        assertThrows(LZFException.class, () -> ChunkDecoderFactory.safeInstance().decode(input));
        assertThrows(LZFException.class, () -> ChunkDecoderFactory.optimalInstance().decode(input));
    }

    @Test
    public void testZeroLengthCompressedChunk() {
        // Compressed chunk that declares both lengths as 0, so it does not even contain a
        // control byte: malformed, and both decoders have to agree that it is
        byte[] chunk = new byte[] {
                LZFChunk.BYTE_Z, LZFChunk.BYTE_V, LZFChunk.BLOCK_TYPE_COMPRESSED,
                0, 0, // compressed length
                0, 0  // uncompressed length
        };
        assertThrows(LZFException.class, () ->
                ChunkDecoderFactory.safeInstance().decodeChunk(chunk, 7, 7, new byte[0], 0, 0));
        assertThrows(LZFException.class, () ->
                ChunkDecoderFactory.optimalInstance().decodeChunk(chunk, 7, 7, new byte[0], 0, 0));

        assertThrows(LZFException.class, () -> ChunkDecoderFactory.safeInstance().decode(chunk));
        assertThrows(LZFException.class, () -> ChunkDecoderFactory.optimalInstance().decode(chunk));
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Second-level test methods
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * `0x20` = short back-reference, run length 3, high bits of offset 0;
     * following `0x00` gives low bits, for an offset of -1
     */
    private final static byte[] SHORT_BACK_REF_MINUS_ONE = new byte[] { 0x20, 0x00 };

    /**
     * `0xE0` = long back-reference, high bits of offset 0; following `0x00` gives
     * run length of 9, and last `0x00` low bits of offset, for an offset of -1
     */
    private final static byte[] LONG_BACK_REF_MINUS_ONE = new byte[] { (byte) 0xE0, 0x00, 0x00 };

    /**
     * Verifies that back-references pointing before the beginning of the chunk's own output
     * are rejected. Offsets of -1 to -8 are handled by the "overlapping" copy paths, which
     * are separate from the bulk copy ones.
     */
    private void _testBackRefBeforeChunkStart(ChunkDecoder decoder)
    {
        // First: back-reference at the very beginning of the output buffer
        assertThrows(LZFException.class, () ->
                decoder.decodeChunk(SHORT_BACK_REF_MINUS_ONE, 0, 2, new byte[3], 0, 3));
        assertThrows(LZFException.class, () ->
                decoder.decodeChunk(LONG_BACK_REF_MINUS_ONE, 0, 3, new byte[9], 0, 9));

        // And then at beginning of the chunk, but not of the buffer: must not reach back
        // into output of an earlier chunk (or into whatever the buffer contained before)
        byte[] output = new byte[32];
        Arrays.fill(output, (byte) 'x');
        assertThrows(LZFException.class, () ->
                decoder.decodeChunk(SHORT_BACK_REF_MINUS_ONE, 0, 2, output, 8, 11));
        assertThrows(LZFException.class, () ->
                decoder.decodeChunk(LONG_BACK_REF_MINUS_ONE, 0, 3, output, 8, 17));
    }

    /**
     * Verifies that a back-reference whose run length would write past the declared
     * uncompressed length of the chunk is rejected (and does not write past it either).
     */
    private void _testBackRefPastChunkEnd(ChunkDecoder decoder)
    {
        // `0x01` = literal run of 2 bytes ('A', 'B'), then a back-reference with a valid
        // offset of -2, but a run length of 3 (short) / 9 (long) although only 2 bytes remain
        byte[] shortBackRef = new byte[] { 0x01, 'A', 'B', 0x20, 0x01 };
        byte[] longBackRef = new byte[] { 0x01, 'A', 'B', (byte) 0xE0, 0x00, 0x01 };

        byte[] output = new byte[16];
        assertThrows(LZFException.class, () -> decoder.decodeChunk(shortBackRef, 0, 5, output, 0, 4));
        assertThrows(LZFException.class, () -> decoder.decodeChunk(longBackRef, 0, 6, output, 0, 4));

        // ... and nothing was written past the declared end of the chunk
        for (int i = 4; i < output.length; ++i) {
            assertEquals(0, output[i], "Wrote past declared uncompressed length, at offset "+i);
        }
    }

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
