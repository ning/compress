package com.ning.compress.lzf;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.code_intelligence.jazzer.mutation.annotation.InRange;
import com.code_intelligence.jazzer.mutation.annotation.NotNull;
import com.code_intelligence.jazzer.mutation.annotation.WithLength;
import com.ning.compress.BufferRecycler;
import com.ning.compress.lzf.impl.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fuzzing test using Jazzer (https://github.com/CodeIntelligenceTesting/jazzer/) for
 * LZF decoder and encoder which uses {@link sun.misc.Unsafe}.
 *
 * <p>By default the tests are run in 'regression mode' where no fuzzing is performed.
 * To run in 'fuzzing mode' set the environment variable {@code JAZZER_FUZZ=1}, see
 * also the {@code pom.xml} of this project.
 *
 * <p>See the Jazzer README for more information.
 */
public class TestFuzzUnsafeLZF {
    /*
     * Important:
     * These fuzz test methods all have to be listed separately in the `pom.xml` to
     * support running them in fuzzing mode, see https://github.com/CodeIntelligenceTesting/jazzer/issues/599
     */

    @FuzzTest(maxDuration = "30s")
    @Retention(RetentionPolicy.RUNTIME)
    @interface LZFFuzzTest {
    }

    // This fuzz test performs decoding twice and verifies that the result is the same (either same decoded value or both exception)
    @LZFFuzzTest
    void decode(byte @NotNull @WithLength(min = 0, max = 32767) [] input, byte @NotNull [] suffix, @InRange(min = 0, max = 32767) int outputSize) {
        byte[] output = new byte[outputSize];
        UnsafeChunkDecoder decoder = new UnsafeChunkDecoder();

        byte[] input1 = input.clone();

        // For the second decoding, append a suffix which should be ignored
        byte[] input2 = new byte[input.length + suffix.length];
        System.arraycopy(input, 0, input2, 0, input.length);
        // Append suffix
        System.arraycopy(suffix, 0, input2, input.length, suffix.length);

        byte[] decoded1 = null;
        try {
            int decodedLen = decoder.decode(input1, 0, input.length, output);
            decoded1 = Arrays.copyOf(output, decodedLen);
        } catch (LZFException | ArrayIndexOutOfBoundsException ignored) {
        }

        // Repeat decoding, this time with (ignored) suffix and prefilled output
        // Should lead to same decoded result
        Arrays.fill(output, (byte) 0xFF);
        byte[] decoded2 = null;
        try {
            int decodedLen = decoder.decode(input2, 0, input.length, output);
            decoded2 = Arrays.copyOf(output, decodedLen);
        } catch (LZFException | ArrayIndexOutOfBoundsException ignored) {
        }

        assertArrayEquals(decoded1, decoded2);

        // Compare with result of vanilla decoder
        byte[] decodedVanilla = null;
        try {
            int decodedLen = new VanillaChunkDecoder().decode(input, output);
            decodedVanilla = Arrays.copyOf(output, decodedLen);
        } catch (Exception ignored) {
        }
        assertArrayEquals(decodedVanilla, decoded1);

    }

    @LZFFuzzTest
    // `boolean dummy` parameter is as workaround for https://github.com/CodeIntelligenceTesting/jazzer/issues/1022
    void roundtrip(byte @NotNull @WithLength(min = 1, max = 32767) [] input, boolean dummy) throws LZFException {
        UnsafeChunkDecoder decoder = new UnsafeChunkDecoder();
        try (UnsafeChunkEncoder encoder = UnsafeChunkEncoders.createEncoder(input.length, new BufferRecycler())) {
            byte[] decoded = decoder.decode(LZFEncoder.encode(encoder, input.clone(), input.length));
            assertArrayEquals(input, decoded);
        }
    }


    // Note: These encoder fuzz tests only cover the encoder implementation matching the platform endianness;
    // don't cover the other endianness here because that could lead to failures simply due to endianness
    // mismatch, and not due to an actual bug in the implementation

    @LZFFuzzTest
    void encode(byte @NotNull @WithLength(min = 1, max = 32767) [] input, byte @NotNull [] suffix) {
        byte[] input1 = input.clone();

        // For the second encoding, append a suffix which should be ignored
        byte[] input2 = new byte[input.length + suffix.length];
        System.arraycopy(input, 0, input2, 0, input.length);
        // Append suffix
        System.arraycopy(suffix, 0, input2, input.length, suffix.length);

        byte[] encoded1;
        try (UnsafeChunkEncoder encoder = UnsafeChunkEncoders.createEncoder(input.length, new BufferRecycler())) {
            encoded1 = LZFEncoder.encode(encoder, input1, input.length);
        }

        byte[] encoded2;
        try (UnsafeChunkEncoder encoder = UnsafeChunkEncoders.createEncoder(input.length, new BufferRecycler())) {
            encoded2 = LZFEncoder.encode(encoder, input2, input.length);
        }
        assertArrayEquals(encoded1, encoded2);

        // Compare with result of vanilla encoder
        byte[] encodedVanilla;
        try (VanillaChunkEncoder encoder = new VanillaChunkEncoder(input.length, new BufferRecycler())) {
            encodedVanilla = LZFEncoder.encode(encoder, input, input.length);
        }
        assertArrayEquals(encodedVanilla, encoded1);
    }

    @LZFFuzzTest
    void encodeAppend(byte @NotNull @WithLength(min = 1, max = 32767) [] input, @InRange(min = 0, max = 32767) int outputSize) {
        byte[] output = new byte[outputSize];
        // Prefill output; should have no effect on encoded result
        Arrays.fill(output, (byte) 0xFF);
        int encodedLen;
        try (UnsafeChunkEncoder encoder = UnsafeChunkEncoders.createEncoder(input.length, new BufferRecycler())) {
            encodedLen = LZFEncoder.appendEncoded(encoder, input.clone(), 0, input.length, output, 0);
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException ignored) {
            // Skip comparison with vanilla encoder
            return;
        }

        byte[] encodedUnsafe = Arrays.copyOf(output, encodedLen);

        // Compare with result of vanilla encoder
        Arrays.fill(output, (byte) 0);
        try (VanillaChunkEncoder encoder = new VanillaChunkEncoder(input.length, new BufferRecycler())) {
            encodedLen = LZFEncoder.appendEncoded(encoder, input, 0, input.length, output, 0);
        }
        // TODO: VanillaChunkEncoder performs out-of-bounds array index whereas UnsafeChunkEncoder does not (not sure which one is correct)
        //   Why do they even have different `_handleTail` implementations, UnsafeChunkEncoder is not using Unsafe there?
        catch (ArrayIndexOutOfBoundsException ignored) {
            return;
        }
        byte[] encodedVanilla = Arrays.copyOf(output, encodedLen);
        assertArrayEquals(encodedVanilla, encodedUnsafe);
    }

    /// Note: Also cover LZFInputStream and LZFOutputStream because they in parts use methods of the decoder and encoder
    /// which are otherwise not reachable

    @LZFFuzzTest
    void inputStreamRead(byte @NotNull @WithLength(min = 0, max = 32767) [] input, @InRange(min = 1, max = 32767) int readBufferSize) throws IOException {
        UnsafeChunkDecoder decoder = new UnsafeChunkDecoder();
        try (LZFInputStream inputStream = new LZFInputStream(decoder, new ByteArrayInputStream(input), new BufferRecycler(), false)) {
            byte[] readBuffer = new byte[readBufferSize];
            while (inputStream.read(readBuffer) != -1) {
                // Do nothing, just consume the data
            }
        } catch (LZFException | ArrayIndexOutOfBoundsException ignored) {
        }
        // TODO: This IndexOutOfBoundsException occurs because LZFInputStream makes an invalid call to ByteArrayInputStream
        //   The reason seems to be that `_inputBuffer` is only MAX_CHUNK_LEN large, but should be `2 + MAX_CHUNK_LEN` to
        //   account for first two bytes encoding the length? (might affect more places in code)
        catch (IndexOutOfBoundsException ignored) {
        }
    }

    @LZFFuzzTest
    void inputStreamSkip(byte @NotNull @WithLength(min = 0, max = 32767) [] input, @InRange(min = 1, max = 32767) int skipCount) throws IOException {
        UnsafeChunkDecoder decoder = new UnsafeChunkDecoder();
        try (LZFInputStream inputStream = new LZFInputStream(decoder, new ByteArrayInputStream(input), new BufferRecycler(), false)) {
            while (inputStream.skip(skipCount) > 0) {
                // Do nothing, just consume the data
            }
        } catch (LZFException ignored) {
        }
        // TODO: This IndexOutOfBoundsException occurs because LZFInputStream makes an invalid call to ByteArrayInputStream
        //   The reason seems to be that `_inputBuffer` is only MAX_CHUNK_LEN large, but should be `2 + MAX_CHUNK_LEN` to
        //   account for first two bytes encoding the length? (might affect more places in code)
        catch (IndexOutOfBoundsException ignored) {
        }
    }

    private static class NullOutputStream extends OutputStream {
        public static final OutputStream INSTANCE = new NullOutputStream();

        private NullOutputStream() {
        }

        @Override
        public void write(int b) {
            // Do nothing
        }

        @Override
        public void write(byte[] b, int off, int len) {
            // Do nothing
        }
    }

    @LZFFuzzTest
    // Generates multiple arrays and writes them separately
    void outputStream(byte @NotNull @WithLength(min = 1, max = 10) [] @NotNull @WithLength(min = 1) [] arrays, @InRange(min = 1, max = 32767) int bufferSize) throws IOException {
        int totalLength = Stream.of(arrays).mapToInt(a -> a.length).sum();

        UnsafeChunkEncoder encoder = UnsafeChunkEncoders.createEncoder(totalLength, new BufferRecycler());
        try (LZFOutputStream outputStream = new LZFOutputStream(encoder, NullOutputStream.INSTANCE, bufferSize, null)) {
            for (byte[] array : arrays) {
                outputStream.write(array);
            }
        }
    }
}
