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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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

    @LZFFuzzTest
    void decode(byte @NotNull [] input, @InRange(min = 0, max = 32767) int outputSize) {
        byte[] output = new byte[outputSize];
        UnsafeChunkDecoder decoder = new UnsafeChunkDecoder();

        try {
            decoder.decode(input, output);
        } catch (LZFException | ArrayIndexOutOfBoundsException ignored) {
        }
    }

    @LZFFuzzTest
    // `boolean dummy` parameter is as workaround for https://github.com/CodeIntelligenceTesting/jazzer/issues/1022
    void roundtrip(byte @NotNull @WithLength(min = 1, max = 32767) [] input, boolean dummy) throws LZFException {
        UnsafeChunkDecoder decoder = new UnsafeChunkDecoder();
        UnsafeChunkEncoder encoder = UnsafeChunkEncoders.createEncoder(input.length, new BufferRecycler());

        byte[] decoded = decoder.decode(LZFEncoder.encode(encoder, input, input.length));
        assertArrayEquals(input, decoded);
    }


    // Note: These encoder fuzz tests only cover the encoder implementation matching the platform endianness;
    // don't cover the other endianness here because that could lead to failures simply due to endianness
    // mismatch, and not due to an actual bug in the implementation

    @LZFFuzzTest
    // `boolean dummy` parameter is as workaround for https://github.com/CodeIntelligenceTesting/jazzer/issues/1022
    void encode(byte @NotNull @WithLength(min = 1, max = 32767) [] input, boolean dummy) {
        UnsafeChunkEncoder encoder = UnsafeChunkEncoders.createEncoder(input.length, new BufferRecycler());
        LZFEncoder.encode(encoder, input, input.length);
    }

    @LZFFuzzTest
    void encodeAppend(byte @NotNull @WithLength(min = 1, max = 32767) [] input, @InRange(min = 0, max = 32767) int outputSize) {
        byte[] output = new byte[outputSize];
        UnsafeChunkEncoder encoder = UnsafeChunkEncoders.createEncoder(input.length, new BufferRecycler());
        try {
            LZFEncoder.appendEncoded(encoder, input, 0, input.length, output, 0);
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException ignored) {
        }
    }

    /// Note: Also cover LZFInputStream and LZFOutputStream because they in parts use methods of the decoder and encoder
    /// which are otherwise not reachable

    @LZFFuzzTest
    void inputStreamRead(byte @NotNull @WithLength(min = 1, max = 32767) [] input, @InRange(min = 1, max = 32767) int readBufferSize) throws IOException {
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
    void inputStreamSkip(byte @NotNull @WithLength(min = 1, max = 32767) [] input, @InRange(min = 1, max = 32767) int skipCount) throws IOException {
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
