package com.ning.compress.lzf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import com.ning.compress.BaseForTests;
import com.ning.compress.lzf.util.ChunkDecoderFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that verify that the "safe" and "optimal" {@link ChunkDecoder} implementations
 * agree on malformed input. They are separate hand-optimized implementations of the same
 * format, with validation duplicated in both, so they can drift apart -- and have: see
 * "#85: Handle malformed LZF back references".
 *<p>
 * For every input both decoders have to either fail with {@link LZFException}, or succeed
 * with identical output. Anything else -- one succeeding where the other fails, differing
 * output, or an exception other than {@code LZFException} for content (as opposed to
 * argument) problems -- is a failure.
 */
public class TestLZFDecoderParity extends BaseForTests
{
    /**
     * Declared uncompressed lengths to try; includes 0, the boundaries of the
     * short (3 - 8 bytes) and long (9 - 264 bytes) back-reference run lengths
     */
    private final static int[] DECLARED_LENGTHS = new int[] { 0, 1, 3, 8, 9, 16, 40, 264, 300 };

    /**
     * Offsets of the chunk within the output buffer: 0, and a non-zero one so that
     * back-references pointing before the chunk (but still within the buffer) are covered
     */
    private final static int[] CHUNK_OFFSETS = new int[] { 0, 5 };

    @Test
    public void testEveryControlByte() {
        byte[][] tails = new byte[][] {
                {}, { 0x00 }, { 0x41 }, { (byte) 0xFF }, { 0x00, 0x00 }, { 0x01, 0x02, 0x03 }
        };
        for (int ctrl = 0; ctrl < 256; ++ctrl) {
            for (byte[] tail : tails) {
                byte[] payload = new byte[1 + tail.length];
                payload[0] = (byte) ctrl;
                System.arraycopy(tail, 0, payload, 1, tail.length);
                for (int declaredLen : DECLARED_LENGTHS) {
                    for (int chunkOffset : CHUNK_OFFSETS) {
                        _assertSameOutcome(payload, chunkOffset, declaredLen);
                    }
                }
            }
        }
    }

    @Test
    public void testRandomPayloads() {
        Random rnd = new Random(1234); // fixed seed: has to stay reproducible
        for (int i = 0; i < 3000; ++i) {
            byte[] payload = new byte[rnd.nextInt(24)];
            rnd.nextBytes(payload);
            _assertSameOutcome(payload, CHUNK_OFFSETS[i & 1], DECLARED_LENGTHS[i % DECLARED_LENGTHS.length]);
        }
    }

    @Test
    public void testTruncatedAndMutatedValidData() throws IOException {
        byte[] orig = "the quick brown fox jumps over the lazy dog, the quick brown fox"
                .getBytes(StandardCharsets.UTF_8);
        // Compressed payload of a single chunk, without the 7 byte header
        byte[] payload = Arrays.copyOfRange(compress(orig), 7, compress(orig).length);

        // Truncated at every possible point ...
        for (int cut = 0; cut <= payload.length; ++cut) {
            byte[] truncated = Arrays.copyOf(payload, cut);
            for (int chunkOffset : CHUNK_OFFSETS) {
                _assertSameOutcome(truncated, chunkOffset, orig.length);
            }
        }
        // ... and with every single byte mutated, to hit control bytes, lengths and offsets
        for (int i = 0; i < payload.length; ++i) {
            for (int mask : new int[] { 0x01, 0x20, 0x80, 0xE0 }) {
                byte[] mutated = payload.clone();
                mutated[i] = (byte) (mutated[i] ^ mask);
                _assertSameOutcome(mutated, 0, orig.length);
            }
        }
    }

    /**
     * Back-reference offsets reach at most 8192 bytes back, so past that point a back-reference
     * provably can not point before the start of the chunk -- which decoders may rely on to skip
     * the check. Verifies the boundary of that reasoning: getting it wrong would let a decoder
     * copy content from before the chunk (or from stale buffer content) without any error.
     *<p>
     * Note that these use chunks larger than 8192 bytes on purpose: the cases above are all far
     * too small to reach the point where such a check can be skipped.
     */
    @Test
    public void testMaxOffsetBackReferenceBoundary() {
        // Short form: control byte with run length 1 (so 3 bytes) and all 5 offset bits set,
        // followed by all 8 bits of the low offset byte -- an offset of -8192
        _testMaxOffsetBackReferences(new byte[] { (byte) 0x3F, (byte) 0xFF }, 3);
        // Long form: control byte marking a long run with all 5 offset bits set, a run length byte
        // of 0 (so 9 bytes), then the low offset byte -- also an offset of -8192. Covered
        // separately because it is checked at a different place in the decoders.
        _testMaxOffsetBackReferences(new byte[] { (byte) 0xFF, 0x00, (byte) 0xFF }, 9);
    }

    private void _testMaxOffsetBackReferences(byte[] backRef, int runLength) {
        // one byte short of the maximum offset being reachable: has to be rejected
        _testMaxOffsetBackReference(backRef, runLength, MAX_BACK_REF_OFFSET - 1, false);
        // exactly at the start of the chunk, and past it: valid
        _testMaxOffsetBackReference(backRef, runLength, MAX_BACK_REF_OFFSET, true);
        _testMaxOffsetBackReference(backRef, runLength, MAX_BACK_REF_OFFSET + 1, true);
        _testMaxOffsetBackReference(backRef, runLength, MAX_BACK_REF_OFFSET + 4096, true);
    }

    /**
     * Verifies that a run overrunning the declared uncompressed length is rejected even far into
     * a large chunk, where checks against the start of the chunk are no longer needed.
     */
    @Test
    public void testLargeChunkOverrun() {
        final int prefixLen = 20000;
        byte[] literals = _literalRuns(prefixLen);
        byte[] payload = Arrays.copyOf(literals, literals.length + 3);
        payload[literals.length] = (byte) 0xE0;   // long back-reference, high bits of offset 0
        payload[literals.length + 1] = (byte) 0xFF; // run length 255 + 9 = 264
        payload[literals.length + 2] = (byte) 0x01; // low bits of offset, for -2

        // Declared length one byte short of what the run produces: rejected
        _assertBothFail(payload, 0, prefixLen + 263, "264 byte run at offset "+prefixLen+", 263 declared");
        // ... and the same content with a matching declared length is valid
        _assertBothSucceed(payload, 0, prefixLen + 264, "264 byte run at offset "+prefixLen);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Second-level test methods
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Maximum offset of a back-reference: 5 bits from the control byte, a full byte, and the
     * implicit 1. Kept here independently of the decoders on purpose, so that changing it there
     * does not silently change what this test verifies.
     */
    private final static int MAX_BACK_REF_OFFSET = 8192;

    private void _testMaxOffsetBackReference(byte[] backRef, int runLength, int outputBeforeBackRef,
            boolean valid)
    {
        // Note: content is added after the back-reference so that it is not close to the end of
        // the chunk. Otherwise a decoder would have to check the run length there anyway, which
        // would catch a bad offset as a side effect -- and this would verify nothing about offsets.
        final int tailLen = 1024;
        byte[] head = _literalRuns(outputBeforeBackRef);
        byte[] tail = _literalRuns(tailLen);
        byte[] payload = new byte[head.length + backRef.length + tail.length];
        System.arraycopy(head, 0, payload, 0, head.length);
        System.arraycopy(backRef, 0, payload, head.length, backRef.length);
        System.arraycopy(tail, 0, payload, head.length + backRef.length, tail.length);

        final int declaredLen = outputBeforeBackRef + runLength + tailLen;
        String desc = runLength+" byte back-reference with offset -"+MAX_BACK_REF_OFFSET
                +" at output offset "+outputBeforeBackRef;

        for (int chunkOffset : CHUNK_OFFSETS) {
            if (valid) {
                byte[] output = _assertBothSucceed(payload, chunkOffset, declaredLen, desc);
                // Copied bytes have to come from exactly 8192 bytes back, which is at or after the
                // start of the chunk; had they come from before it, they would be the prefill
                final int copiedFrom = outputBeforeBackRef - MAX_BACK_REF_OFFSET;
                for (int i = 0; i < runLength; ++i) {
                    assertEquals(output[chunkOffset + copiedFrom + i],
                            output[chunkOffset + outputBeforeBackRef + i],
                            desc+": byte "+i+" of the run was not copied from output offset "+copiedFrom);
                }
            } else {
                _assertBothFail(payload, chunkOffset, declaredLen, desc);
            }
        }
    }

    /**
     * Builds a payload of literal runs producing exactly given number of output bytes
     */
    private byte[] _literalRuns(int length)
    {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        int written = 0;
        while (written < length) {
            int run = Math.min(LZFChunk.MAX_LITERAL, length - written);
            payload.write(run - 1); // control byte of a literal run of N bytes is N-1
            for (int i = 0; i < run; ++i) {
                payload.write('A' + ((written + i) % 26));
            }
            written += run;
        }
        return payload.toByteArray();
    }

    private void _assertBothFail(byte[] payload, int chunkOffset, int declaredLen, String desc)
    {
        for (ChunkDecoder decoder : new ChunkDecoder[] { _safe, _optimal }) {
            byte[] output = new byte[chunkOffset + declaredLen];
            Arrays.fill(output, (byte) 'P');
            assertNotNull(_decode(decoder, payload, chunkOffset, declaredLen, output),
                    decoder.getClass().getSimpleName()+" accepted invalid content: "+desc
                            +" (chunk offset "+chunkOffset+")");
        }
    }

    /**
     * @return Output produced by the decoders, which has to be identical for both
     */
    private byte[] _assertBothSucceed(byte[] payload, int chunkOffset, int declaredLen, String desc)
    {
        byte[] result = null;
        for (ChunkDecoder decoder : new ChunkDecoder[] { _safe, _optimal }) {
            byte[] output = new byte[chunkOffset + declaredLen];
            Arrays.fill(output, (byte) 'P');
            LZFException failure = _decode(decoder, payload, chunkOffset, declaredLen, output);
            assertNull(failure, decoder.getClass().getSimpleName()+" rejected valid content: "+desc
                    +" (chunk offset "+chunkOffset+")");
            if (result == null) {
                result = output;
            } else {
                assertArrayEquals(result, output, "Decoders produced different output for "+desc);
            }
        }
        return result;
    }

    // Note: `decodeChunk(byte[], int, int, byte[], int, int)` does not use decoder state,
    // so single instances can be reused for all cases
    private final ChunkDecoder _safe = ChunkDecoderFactory.safeInstance();
    private final ChunkDecoder _optimal = ChunkDecoderFactory.optimalInstance();

    private void _assertSameOutcome(byte[] payload, int chunkOffset, int declaredLen)
    {
        byte[] safeOutput = new byte[chunkOffset + declaredLen];
        byte[] optimalOutput = new byte[chunkOffset + declaredLen];
        // Prefill, so that content of an earlier chunk (or stale buffer content) is
        // distinguishable from output actually produced for this chunk
        Arrays.fill(safeOutput, (byte) 'P');
        Arrays.fill(optimalOutput, (byte) 'P');

        LZFException safeFailure = _decode(_safe, payload, chunkOffset, declaredLen, safeOutput);
        LZFException optimalFailure = _decode(_optimal, payload, chunkOffset, declaredLen, optimalOutput);

        if ((safeFailure == null) != (optimalFailure == null)) {
            fail("Decoders disagree for "+_describe(payload, chunkOffset, declaredLen)
                    +": safe "+_outcome(safeFailure)+", optimal "+_outcome(optimalFailure));
        }
        if (safeFailure == null) {
            assertArrayEquals(safeOutput, optimalOutput,
                    "Decoders produced different output for "+_describe(payload, chunkOffset, declaredLen));
        }
    }

    /**
     * @return `LZFException` thrown by the decoder, if any; `null` if decoding succeeded.
     *   Fails the test for any other exception: arguments passed are valid, so content
     *   problems have to be reported as `LZFException`
     */
    private LZFException _decode(ChunkDecoder decoder, byte[] payload, int chunkOffset, int declaredLen,
            byte[] output)
    {
        try {
            decoder.decodeChunk(payload, 0, payload.length, output, chunkOffset, chunkOffset + declaredLen);
            return null;
        } catch (LZFException e) {
            return e;
        } catch (RuntimeException e) {
            fail(decoder.getClass().getSimpleName()+" threw "+e.getClass().getName()
                    +" instead of LZFException for "+_describe(payload, chunkOffset, declaredLen), e);
            return null; // never gets here
        }
    }

    private String _outcome(LZFException e) {
        return (e == null) ? "succeeded" : "failed ("+e.getMessage()+")";
    }

    private String _describe(byte[] payload, int chunkOffset, int declaredLen) {
        StringBuilder sb = new StringBuilder("payload 0x");
        for (byte b : payload) {
            sb.append(String.format("%02x", b));
        }
        return sb.append(", chunk offset ").append(chunkOffset)
                .append(", declared uncompressed length ").append(declaredLen).toString();
    }
}
