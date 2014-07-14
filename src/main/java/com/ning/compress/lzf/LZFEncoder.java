/* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.ning.compress.lzf;

import com.ning.compress.BufferRecycler;
import com.ning.compress.lzf.util.ChunkEncoderFactory;

/**
 * Encoder that handles splitting of input into chunks to encode,
 * calls {@link ChunkEncoder} to compress individual chunks and
 * combines resulting chunks into contiguous output byte array.
 * 
 * @author Tatu Saloranta
 */
public class LZFEncoder
{
    /* Approximate maximum size for a full chunk, in case where it does not compress
     * at all. Such chunks are converted to uncompressed chunks, but during compression
     * process this amount of space is still needed.
     */
    public final static int MAX_CHUNK_RESULT_SIZE = LZFChunk.MAX_HEADER_LEN + LZFChunk.MAX_CHUNK_LEN + (LZFChunk.MAX_CHUNK_LEN * 32 / 31);

    // Static methods only, no point in instantiating
    private LZFEncoder() { }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Helper method that can be used to estimate maximum space needed to
     * try compression of given amount of data. This is slightly larger
     * than maximum resulting content since compressor has a choice of
     * uncompressed chunks to use, but that is only done after compression
     * fails to reduce size; and this temporary expansion of up to 3.3% or so
     * (1 indicator for every 31 bytes of uncompressed data)
     * is more than what eventual expansion would be (5 bytes header per
     * each uncompressed chunk, usually 0.01%).
     */
    public static int estimateMaxWorkspaceSize(int inputSize)
    {
        // single chunk; give a rough estimate with +5% (1 + 1/32 + 1/64)
        if (inputSize <= LZFChunk.MAX_CHUNK_LEN) {
            return LZFChunk.MAX_HEADER_LEN + inputSize + (inputSize >> 5) + (inputSize >> 6);
        }
        // one more special case, 2 chunks
        inputSize -= LZFChunk.MAX_CHUNK_LEN;
        if (inputSize <= LZFChunk.MAX_CHUNK_LEN) { // uncompressed chunk actually has 5 byte header but
            return MAX_CHUNK_RESULT_SIZE + inputSize + LZFChunk.MAX_HEADER_LEN;
        }
        // check number of chunks we should be creating (assuming use of full chunks)
        int chunkCount = 1 + ((inputSize + (LZFChunk.MAX_CHUNK_LEN-1)) / LZFChunk.MAX_CHUNK_LEN);
        return MAX_CHUNK_RESULT_SIZE + chunkCount * (LZFChunk.MAX_CHUNK_LEN + LZFChunk.MAX_HEADER_LEN);
    }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Encoding methods, blocks
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Method for compressing given input data using LZF encoding and
     * block structure (compatible with lzf command line utility).
     * Result consists of a sequence of chunks.
     *<p>
     * Note that {@link ChunkEncoder} instance used is one produced by
     * {@link ChunkEncoderFactory#optimalInstance}, which typically
     * is "unsafe" instance if one can be used on current JVM.
     */
    public static byte[] encode(byte[] data) {
        return encode(data, 0, data.length);
    }

    /**
     * Method that will use "safe" {@link ChunkEncoder}, as produced by
     * {@link ChunkEncoderFactory#safeInstance}, for encoding. Safe here
     * means that it does not use any non-compliant features beyond core JDK.
     */
    public static byte[] safeEncode(byte[] data) {
        return safeEncode(data, 0, data.length);
    }

    /**
     * Method for compressing given input data using LZF encoding and
     * block structure (compatible with lzf command line utility).
     * Result consists of a sequence of chunks.
     *<p>
     * Note that {@link ChunkEncoder} instance used is one produced by
     * {@link ChunkEncoderFactory#optimalInstance}, which typically
     * is "unsafe" instance if one can be used on current JVM.
     */
    public static byte[] encode(byte[] data, int offset, int length)
    {
        ChunkEncoder enc = ChunkEncoderFactory.optimalInstance(length);
        byte[] result = encode(enc, data, offset, length);
        enc.close(); // important for buffer reuse!
        return result;
    }

    /**
     * Method that will use "safe" {@link ChunkEncoder}, as produced by
     * {@link ChunkEncoderFactory#safeInstance}, for encoding. Safe here
     * means that it does not use any non-compliant features beyond core JDK.
     */
    public static byte[] safeEncode(byte[] data, int offset, int length)
    {
        ChunkEncoder enc = ChunkEncoderFactory.safeInstance(length);
        byte[] result = encode(enc, data, offset, length);
        enc.close();
        return result;
    }    

    /**
     * Method for compressing given input data using LZF encoding and
     * block structure (compatible with lzf command line utility).
     * Result consists of a sequence of chunks.
     *<p>
     * Note that {@link ChunkEncoder} instance used is one produced by
     * {@link ChunkEncoderFactory#optimalInstance}, which typically
     * is "unsafe" instance if one can be used on current JVM.
     */
    public static byte[] encode(byte[] data, int offset, int length, BufferRecycler bufferRecycler)
    {
        ChunkEncoder enc = ChunkEncoderFactory.optimalInstance(length, bufferRecycler);
        byte[] result = encode(enc, data, offset, length);
        enc.close(); // important for buffer reuse!
        return result;
    }

    /**
     * Method that will use "safe" {@link ChunkEncoder}, as produced by
     * {@link ChunkEncoderFactory#safeInstance}, for encoding. Safe here
     * means that it does not use any non-compliant features beyond core JDK.
     */
    public static byte[] safeEncode(byte[] data, int offset, int length, BufferRecycler bufferRecycler)
    {
        ChunkEncoder enc = ChunkEncoderFactory.safeInstance(length, bufferRecycler);
        byte[] result = encode(enc, data, offset, length);
        enc.close();
        return result;
    }    

    /**
     * Compression method that uses specified {@link ChunkEncoder} for actual
     * encoding.
     */
    public static byte[] encode(ChunkEncoder enc, byte[] data, int length) {
        return encode(enc, data, 0, length);
    }

    /**
     * Method that encodes given input using provided {@link ChunkEncoder},
     * and aggregating it into a single byte array and returning that.
     *<p>
     * NOTE: method does NOT call {@link ChunkEncoder#close}; caller is responsible
     * for doing that after it is done using the encoder.
     */
    public static byte[] encode(ChunkEncoder enc, byte[] data, int offset, int length)
    {
        int left = length;
        int chunkLen = Math.min(LZFChunk.MAX_CHUNK_LEN, left);
        LZFChunk first = enc.encodeChunk(data, offset, chunkLen);
        left -= chunkLen;
        // shortcut: if it all fit in, no need to coalesce:
        if (left < 1) {
            return first.getData();
        }
        // otherwise need to get other chunks:
        int resultBytes = first.length();
        offset += chunkLen;
        LZFChunk last = first;

        do {
            chunkLen = Math.min(left, LZFChunk.MAX_CHUNK_LEN);
            LZFChunk chunk = enc.encodeChunk(data, offset, chunkLen);
            offset += chunkLen;
            left -= chunkLen;
            resultBytes += chunk.length();
            last.setNext(chunk);
            last = chunk;
        } while (left > 0);
        // and then coalesce returns into single contiguous byte array
        byte[] result = new byte[resultBytes];
        int ptr = 0;
        for (; first != null; first = first.next()) {
            ptr = first.copyTo(result, ptr);
        }
        return result;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Encoding methods, append in caller-provided buffer(s)
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Alternate version that accepts pre-allocated output buffer.
     *<p>
     * Note that {@link ChunkEncoder} instance used is one produced by
     * {@link ChunkEncoderFactory#optimalNonAllocatingInstance}, which typically
     * is "unsafe" instance if one can be used on current JVM.
     */
    public static int appendEncoded(byte[] input, int inputPtr, int inputLength,
            byte[] outputBuffer, int outputPtr) {
        ChunkEncoder enc = ChunkEncoderFactory.optimalNonAllocatingInstance(inputLength);
        int len = appendEncoded(enc, input, inputPtr, inputLength, outputBuffer, outputPtr);
        enc.close();
        return len;
    }

    /**
     * Alternate version that accepts pre-allocated output buffer.
     *<p>
     * Method that will use "safe" {@link ChunkEncoder}, as produced by
     * {@link ChunkEncoderFactory#safeInstance}, for encoding. Safe here
     * means that it does not use any non-compliant features beyond core JDK.
     */
    public static int safeAppendEncoded(byte[] input, int inputPtr, int inputLength,
            byte[] outputBuffer, int outputPtr) {
        ChunkEncoder enc = ChunkEncoderFactory.safeNonAllocatingInstance(inputLength);
        int len = appendEncoded(enc, input, inputPtr, inputLength, outputBuffer, outputPtr);
        enc.close();
        return len;
    }
    
    /**
     * Alternate version that accepts pre-allocated output buffer.
     *<p>
     * Note that {@link ChunkEncoder} instance used is one produced by
     * {@link ChunkEncoderFactory#optimalNonAllocatingInstance}, which typically
     * is "unsafe" instance if one can be used on current JVM.
     */
    public static int appendEncoded(byte[] input, int inputPtr, int inputLength,
            byte[] outputBuffer, int outputPtr, BufferRecycler bufferRecycler) {
        ChunkEncoder enc = ChunkEncoderFactory.optimalNonAllocatingInstance(inputLength, bufferRecycler);
        int len = appendEncoded(enc, input, inputPtr, inputLength, outputBuffer, outputPtr);
        enc.close();
        return len;
    }

    /**
     * Alternate version that accepts pre-allocated output buffer.
     *<p>
     * Method that will use "safe" {@link ChunkEncoder}, as produced by
     * {@link ChunkEncoderFactory#safeInstance}, for encoding. Safe here
     * means that it does not use any non-compliant features beyond core JDK.
     */
    public static int safeAppendEncoded(byte[] input, int inputPtr, int inputLength,
            byte[] outputBuffer, int outputPtr, BufferRecycler bufferRecycler) {
        ChunkEncoder enc = ChunkEncoderFactory.safeNonAllocatingInstance(inputLength, bufferRecycler);
        int len = appendEncoded(enc, input, inputPtr, inputLength, outputBuffer, outputPtr);
        enc.close();
        return len;
    }

	/**
     * Alternate version that accepts pre-allocated output buffer.
     */
    public static int appendEncoded(ChunkEncoder enc, byte[] input, int inputPtr, int inputLength,
            byte[] outputBuffer, int outputPtr)
    {
        int left = inputLength;
        int chunkLen = Math.min(LZFChunk.MAX_CHUNK_LEN, left);

        outputPtr = enc.appendEncodedChunk(input, inputPtr, chunkLen, outputBuffer, outputPtr);
        left -= chunkLen;
        // shortcut: if it all fit in, no need to coalesce:
        if (left < 1) {
            return outputPtr;
        }
        // otherwise need to keep on encoding...
        inputPtr += chunkLen;
        do {
            chunkLen = Math.min(left, LZFChunk.MAX_CHUNK_LEN);
            outputPtr = enc.appendEncodedChunk(input, inputPtr, chunkLen, outputBuffer, outputPtr);
            inputPtr += chunkLen;
            left -= chunkLen;
        } while (left > 0);
        return outputPtr;
    }
}
