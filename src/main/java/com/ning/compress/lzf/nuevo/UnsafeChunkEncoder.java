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

package com.ning.compress.lzf.nuevo;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteOrder;

import sun.misc.Unsafe;

import com.ning.compress.BufferRecycler;
import com.ning.compress.lzf.LZFChunk;

/**
 * Class that handles actual encoding of individual chunks.
 * Resulting chunks can be compressed or non-compressed; compression
 * is only used if it actually reduces chunk size (including overhead
 * of additional header bytes)
 * 
 * @author Tatu Saloranta (tatu.saloranta@iki.fi)
 */
@SuppressWarnings("restriction")
public final class UnsafeChunkEncoder
{
    // Beyond certain point we won't be able to compress; let's use 16 bytes as cut-off
    private static final int MIN_BLOCK_TO_COMPRESS = 16;

    private static final int MIN_HASH_SIZE = 256;

    // Not much point in bigger tables, with 8k window
    private static final int MAX_HASH_SIZE = 16384;

    private static final int MAX_OFF = 1 << 13; // 8k
    private static final int MAX_REF = (1 << 8) + (1 << 3); // 264

    /**
     * How many tail bytes are we willing to just copy as is, to simplify
     * loop end checks? 4 is bare minimum, may be raised to 8?
     */
    private static final int TAIL_LENGTH = 4;
    
    // // Encoding tables etc

    private final BufferRecycler _recycler;

    /**
     * Hash table contains lookup based on 3-byte sequence; key is hash
     * of such triplet, value is offset in buffer.
     */
    private int[] _hashTable;
    
    private final int _hashModulo;

    /**
     * Buffer in which encoded content is stored during processing
     */
    private byte[] _encodeBuffer;

    /**
     * Small buffer passed to LZFChunk, needed for writing chunk header
     */
    private byte[] _headerBuffer;
    
    /**
     * @param totalLength Total encoded length; used for calculating size
     *   of hash table to use
     */
    public UnsafeChunkEncoder(int totalLength)
    {
        // Need room for at most a single full chunk
        int largestChunkLen = Math.min(totalLength, LZFChunk.MAX_CHUNK_LEN);       
        int suggestedHashLen = calcHashLen(largestChunkLen);
        _recycler = BufferRecycler.instance();
        _hashTable = _recycler.allocEncodingHash(suggestedHashLen);
        _hashModulo = _hashTable.length - 1;
        // Ok, then, what's the worst case output buffer length?
        // length indicator for each 32 literals, so:
        // 21-Feb-2013, tatu: Plus we want to prepend chunk header in place:
        int bufferLen = largestChunkLen + ((largestChunkLen + 31) >> 5) + LZFChunk.MAX_HEADER_LEN;
        _encodeBuffer = _recycler.allocEncodingBuffer(bufferLen);
    }

    /**
     * Alternate constructor used when we want to avoid allocation encoding
     * buffer, in cases where caller wants full control over allocations.
     */
    private UnsafeChunkEncoder(int totalLength, boolean bogus)
    {
        int largestChunkLen = Math.max(totalLength, LZFChunk.MAX_CHUNK_LEN);
        int suggestedHashLen = calcHashLen(largestChunkLen);
        _recycler = BufferRecycler.instance();
        _hashTable = _recycler.allocEncodingHash(suggestedHashLen);
        _hashModulo = _hashTable.length - 1;
        _encodeBuffer = null;
    }

    public static UnsafeChunkEncoder nonAllocatingEncoder(int totalLength) {
        return new UnsafeChunkEncoder(totalLength, true);
    }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////
     */
    
    /**
     * Method to close once encoder is no longer in use. Note: after calling
     * this method, further calls to {@link #encodeChunk} will fail
     */
    public void close()
    {
        byte[] buf = _encodeBuffer;
        if (buf != null) {
            _encodeBuffer = null;
            _recycler.releaseEncodeBuffer(buf);
        }
        int[] ibuf = _hashTable;
        if (ibuf != null) {
            _hashTable = null;
            _recycler.releaseEncodingHash(ibuf);
        }
    }
    
    /**
     * Method for compressing (or not) individual chunks
     */
    public LZFChunk encodeChunk(byte[] data, int offset, int len)
    {
        if (len >= MIN_BLOCK_TO_COMPRESS) {
            /* If we have non-trivial block, and can compress it by at least
             * 2 bytes (since header is 2 bytes longer), let's compress:
             */
            int compLen = tryCompress(data, offset, offset+len, _encodeBuffer, 0);
            if (compLen < (len-2)) { // nah; just return uncompressed
                return LZFChunk.createCompressed(len, _encodeBuffer, 0, compLen);
            }
        }
        // Otherwise leave uncompressed:
        return LZFChunk.createNonCompressed(data, offset, len);
    }

    /**
     * Alternate chunk compression method that will append encoded chunk in
     * pre-allocated buffer. Note that caller must ensure that the buffer is
     * large enough to hold not just encoded result but also intermediate
     * result; latter may be up to 4% larger than input; caller may use
     * {@link UnsafeLZFEncoder#estimateMaxWorkspaceSize(int)} to calculate
     * necessary buffer size.
     * 
     * @return Offset in output buffer after appending the encoded chunk
     * 
     * @since 0.9.7
     */
    public int appendEncodedChunk(final byte[] input, final int inputPtr, final int inputLen,
            final byte[] outputBuffer, final int outputPos)
    {
        if (inputLen >= MIN_BLOCK_TO_COMPRESS) {
            /* If we have non-trivial block, and can compress it by at least
             * 2 bytes (since header is 2 bytes longer), use as-is
             */
            final int compStart = outputPos + LZFChunk.HEADER_LEN_COMPRESSED;
            final int end = tryCompress(input, inputPtr, inputPtr+inputLen, outputBuffer, compStart);
            final int uncompEnd = (outputPos + LZFChunk.HEADER_LEN_NOT_COMPRESSED) + inputLen;
            if (end < uncompEnd) { // yes, compressed by at least one byte
                final int compLen = end - compStart;
                LZFChunk.appendCompressedHeader(inputLen, compLen, outputBuffer, outputPos);
                return end;
            }
        }
        // Otherwise append as non-compressed chunk instead (length + 5):
        return LZFChunk.appendNonCompressed(input, inputPtr, inputLen, outputBuffer, outputPos);
    }
    
    /**
     * Method for encoding individual chunk, writing it to given output stream.
     */
    public void encodeAndWriteChunk(byte[] data, int offset, int len, OutputStream out)
        throws IOException
    {
        if (len >= MIN_BLOCK_TO_COMPRESS) {
            // If we have non-trivial block, and can compress it by at least
            // 2 bytes (since header is 2 bytes longer), let's compress:
            int compEnd = tryCompress(data, offset, offset+len, _encodeBuffer, LZFChunk.HEADER_LEN_COMPRESSED);
            final int compLen = compEnd - LZFChunk.HEADER_LEN_COMPRESSED;
            if (compLen < (len-2)) { // yes, compressed block is smaller (consider header is 2 bytes longer)
                LZFChunk.appendCompressedHeader(len, compLen, _encodeBuffer, 0);
                out.write(_encodeBuffer, 0, compEnd);
                return;
            }
        }
        // Otherwise leave uncompressed:
        byte[] headerBuf = _headerBuffer;
        if (headerBuf == null) {
            _headerBuffer = headerBuf = new byte[LZFChunk.MAX_HEADER_LEN];
        }
        LZFChunk.writeNonCompressedHeader(len, out, headerBuf);
        out.write(data, offset, len);
    }

    /**
     * Main workhorse method that will try to compress given chunk, and return
     * end position (offset to byte after last included byte)
     * 
     * @return Output pointer after handling content, such that <code>result - originalOutPost</code>
     *    is the actual length of compressed chunk (without header)
     */
    protected int tryCompress(byte[] in, int inPos, int inEnd, byte[] out, int outPos)
    {
        final int[] hashTable = _hashTable;
        int seen = first(in, 0); // past 4 bytes we have seen... (last one is LSB)
        
        int literals = 0;
        inEnd -= TAIL_LENGTH;
        final int firstPos = inPos; // so that we won't have back references across block boundary

        while (inPos < inEnd) {
            seen = unsafe.getInt(in, BYTE_ARRAY_OFFSET + inPos - 1);
            if (IS_LITTLE_ENDIAN) {
                seen = Integer.reverseBytes(seen);
            }
            int off = hash(seen);
            int ref = hashTable[off];
            hashTable[off] = inPos;
  
            // First expected common case: no back-ref (for whatever reason)
            if (ref >= inPos // can't refer forward (i.e. leftovers)
                    || ref < firstPos // or to previous block
                    || (off = inPos - ref) > MAX_OFF
                    || _nonMatch(seen, in, ref)) {
                ++inPos;
                ++literals;
                if (literals == LZFChunk.MAX_LITERAL) {
                    outPos = _copyFullLiterals(in, inPos, out, outPos);
                    literals = 0;
                }
                continue;
            }
            // match
            int maxLen = inEnd - inPos + 2;
            if (maxLen > MAX_REF) {
                maxLen = MAX_REF;
            }
            if (literals > 0) {
                outPos = _copyPartialLiterals(in, inPos, out, outPos, literals);
                literals = 0;
            }
            int len = _findMatchLength(in, ref+3, inPos+3, ref+maxLen);
            
            --off; // was off by one earlier
            if (len < 7) {
                out[outPos++] = (byte) ((off >> 8) + (len << 5));
            } else {
                out[outPos++] = (byte) ((off >> 8) + (7 << 5));
                out[outPos++] = (byte) (len - 7);
            }
            out[outPos++] = (byte) off;
            inPos += len;
            int value = unsafe.getInt(in, BYTE_ARRAY_OFFSET + inPos);
            if (IS_LITTLE_ENDIAN) {
                value = Integer.reverseBytes(value);
            }
            hashTable[hash(value >> 8)] = inPos;
            ++inPos;
            hashTable[hash(value)] = inPos;
            ++inPos;
        }
        // try offlining the tail
        return handleTail(in, inPos, inEnd+4, out, outPos, literals);
    }

    private final boolean _nonMatch(int seen, byte[] in, int inPos)
    {
        int value = unsafe.getInt(in, BYTE_ARRAY_OFFSET + inPos - 1);
        if (IS_LITTLE_ENDIAN) {
            value = Integer.reverseBytes(value);
        }
        return (seen << 8) != (value << 8);
    }   
                
    private final int handleTail(byte[] in, int inPos, int inEnd, byte[] out, int outPos,
            int literals)
    {
        while (inPos < inEnd) {
            ++inPos;
            ++literals;
            if (literals == LZFChunk.MAX_LITERAL) {
                out[outPos++] = (byte) (literals-1); // <= out[outPos - literals - 1] = MAX_LITERAL_MINUS_1;
                System.arraycopy(in, inPos-literals, out, outPos, literals);
                outPos += literals;
                literals = 0;
            }
        }
        if (literals > 0) {
            out[outPos++] = (byte) (literals - 1);
            System.arraycopy(in, inPos-literals, out, outPos, literals);
            outPos += literals;
        }
        return outPos;
    }

    private final static int _findMatchLength(final byte[] in, int ptr1, int ptr2, final int maxPtr1)
    {
        // Expect at least 8 bytes to check for fast case; offline others
        if ((ptr1 + 8) >= maxPtr1) { // rare case, offline
            return _findTailMatchLength(in, ptr1, ptr2, maxPtr1);
        }
        // short matches common, so start with specialized comparison
        // NOTE: we know that we have 4 bytes of slack before end, so this is safe:
        int i1 = unsafe.getInt(in, BYTE_ARRAY_OFFSET + ptr1);
        int i2 = unsafe.getInt(in, BYTE_ARRAY_OFFSET + ptr2);
        if (i1 != i2) {
            return 1 + _leadingBytes(i1, i2);
        }
        ptr1 += 4;
        ptr2 += 4;

        i1 = unsafe.getInt(in, BYTE_ARRAY_OFFSET + ptr1);
        i2 = unsafe.getInt(in, BYTE_ARRAY_OFFSET + ptr2);
        if (i1 != i2) {
            return 5 + _leadingBytes(i1, i2);
        }
        return _findLongMatchLength(in, ptr1+4, ptr2+4, maxPtr1);
    }

    private final static int _findLongMatchLength(final byte[] in, int ptr1, int ptr2, final int maxPtr1)
    {
        final int base = ptr1 - 9;
        // and then just loop with longs if we get that far
        final int longEnd = maxPtr1-8;
        while (ptr1 <= longEnd) {
            long l1 = unsafe.getLong(in, BYTE_ARRAY_OFFSET + ptr1);
            long l2 = unsafe.getLong(in, BYTE_ARRAY_OFFSET + ptr2);
            if (l1 != l2) {
                long xor = l1 ^ l2;
                int zeroBits = IS_LITTLE_ENDIAN ? Long.numberOfTrailingZeros(xor) : Long.numberOfLeadingZeros(xor);
                return ptr1 - base + (zeroBits >> 3);
            }
            ptr1 += 8;
            ptr2 += 8;
        }
        // or, if running out of runway, handle last bytes with loop-de-loop...
        while (ptr1 < maxPtr1 && in[ptr1] == in[ptr2]) {
            ++ptr1;
            ++ptr2;
        }
        return ptr1 - base; // i.e. 
    }

    private final static int _leadingBytes(int i1, int i2) {
        int xor = i1 ^ i2;
        int zeroBits = IS_LITTLE_ENDIAN ? Long.numberOfTrailingZeros(xor) : Long.numberOfLeadingZeros(xor);
        return (zeroBits >> 3);
    }
    
    private final static int _findTailMatchLength(final byte[] in, int ptr1, int ptr2, final int maxPtr1)
    {
        final int start1 = ptr1;
        while (ptr1 < maxPtr1 && in[ptr1] == in[ptr2]) {
            ++ptr1;
            ++ptr2;
        }
        return ptr1 - start1 + 1; // i.e. 
    }
    
    private final static int _copyPartialLiterals(byte[] in, int inPos, byte[] out, int outPos,
            int literals)
    {
        out[outPos++] = (byte) (literals-1);

        // Here use of Unsafe is clear win:
        
//        System.arraycopy(in, inPos-literals, out, outPos, literals);

        long rawInPtr = BYTE_ARRAY_OFFSET + inPos - literals;
        long rawOutPtr= BYTE_ARRAY_OFFSET + outPos;

        switch (literals >> 3) {
        case 3:
            unsafe.putLong(out, rawOutPtr, unsafe.getLong(in, rawInPtr));
            rawInPtr += 8;
            rawOutPtr += 8;
        case 2:
            unsafe.putLong(out, rawOutPtr, unsafe.getLong(in, rawInPtr));
            rawInPtr += 8;
            rawOutPtr += 8;
        case 1:
            unsafe.putLong(out, rawOutPtr, unsafe.getLong(in, rawInPtr));
            rawInPtr += 8;
            rawOutPtr += 8;
        }
        unsafe.putLong(out, rawOutPtr, unsafe.getLong(in, rawInPtr));
        int left = (literals & 7);
        if (left > 4) {
            unsafe.putLong(out, rawOutPtr, unsafe.getLong(in, rawInPtr));
        } else {
            unsafe.putInt(out, rawOutPtr, unsafe.getInt(in, rawInPtr));
        }

        return outPos+literals;
    }

    private final static int _copyFullLiterals(byte[] in, int inPos, byte[] out, int outPos)
    {
        // literals == 32
        out[outPos++] = (byte) 31;

        // But here it's bit of a toss, since this gets rarely called
        
//        System.arraycopy(in, inPos-32, out, outPos, 32);

        long rawInPtr = BYTE_ARRAY_OFFSET + inPos - 32;
        long rawOutPtr = BYTE_ARRAY_OFFSET + outPos;
    
        unsafe.putLong(out, rawOutPtr, unsafe.getLong(in, rawInPtr));
        rawInPtr += 8;
        rawOutPtr += 8;
        unsafe.putLong(out, rawOutPtr, unsafe.getLong(in, rawInPtr));
        rawInPtr += 8;
        rawOutPtr += 8;
        unsafe.putLong(out, rawOutPtr, unsafe.getLong(in, rawInPtr));
        rawInPtr += 8;
        rawOutPtr += 8;
        unsafe.putLong(out, rawOutPtr, unsafe.getLong(in, rawInPtr));

        return (outPos + 32);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////////////////////////
     */
    
    private static int calcHashLen(int chunkSize)
    {
        // in general try get hash table size of 2x input size
        chunkSize += chunkSize;
        // but no larger than max size:
        if (chunkSize >= MAX_HASH_SIZE) {
            return MAX_HASH_SIZE;
        }
        // otherwise just need to round up to nearest 2x
        int hashLen = MIN_HASH_SIZE;
        while (hashLen < chunkSize) {
            hashLen += hashLen;
        }
        return hashLen;
    }

    private final int first(byte[] in, int inPos) {
//        return (in[inPos] << 8) + (in[inPos + 1] & 0xFF);
        short v = unsafe.getShort(in, BYTE_ARRAY_OFFSET + inPos);
        if (IS_LITTLE_ENDIAN) {
            return Short.reverseBytes(v);
        }
        return v;
    }

    private final int hash(int h) {
        // or 184117; but this seems to give better hashing?
        return ((h * 57321) >> 9) & _hashModulo;
        // original lzf-c.c used this:
        //return (((h ^ (h << 5)) >> (24 - HLOG) - h*5) & _hashModulo;
        // but that didn't seem to provide better matches
    }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Alternative experimental version using Unsafe
    // NOTE: not currently used, retained for future inspiration...
    ///////////////////////////////////////////////////////////////////////
     */

    private static final Unsafe unsafe;
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final long BYTE_ARRAY_OFFSET = unsafe.arrayBaseOffset(byte[].class);

    private static final boolean IS_LITTLE_ENDIAN = (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN);
    
    /*
    private final int MASK = 0xFFFFFF;
    
    private final int get3Bytes(byte[] src, int srcIndex)
    {
        return unsafe.getInt(src, BYTE_ARRAY_OFFSET + srcIndex) & MASK;
    }
    */

    /*
    private int tryCompress(byte[] in, int inPos, int inEnd, byte[] out, int outPos)
    {
        final int[] hashTable = _hashTable;
        ++outPos;
        int literals = 0;
        inEnd -= 4;
        final int firstPos = inPos; // so that we won't have back references across block boundary
        
        while (inPos < inEnd) {
            int seen = get3Bytes(in, inPos);
            int off = hash(seen);
            int ref = hashTable[off];
            hashTable[off] = inPos;
  
            // First expected common case: no back-ref (for whatever reason)
            if (ref >= inPos // can't refer forward (i.e. leftovers)
                    || ref < firstPos // or to previous block
                    || (off = inPos - ref) > MAX_OFF
                    || get3Bytes(in, ref) != seen
                    ) {
                out[outPos++] = in[inPos++];
                literals++;
                if (literals == LZFChunk.MAX_LITERAL) {
                    out[outPos - 33] = (byte) 31; // <= out[outPos - literals - 1] = MAX_LITERAL_MINUS_1;
                    literals = 0;
                    outPos++;
                }
                continue;
            }
            // match
            int maxLen = inEnd - inPos + 2;
            if (maxLen > MAX_REF) {
                maxLen = MAX_REF;
            }
            if (literals == 0) {
                outPos--;
            } else {
                out[outPos - literals - 1] = (byte) (literals - 1);
                literals = 0;
            }
            int len = 3;
            while (len < maxLen && in[ref + len] == in[inPos + len]) {
                len++;
            }
            len -= 2;
            --off; // was off by one earlier
            if (len < 7) {
                out[outPos++] = (byte) ((off >> 8) + (len << 5));
            } else {
                out[outPos++] = (byte) ((off >> 8) + (7 << 5));
                out[outPos++] = (byte) (len - 7);
            }
            out[outPos] = (byte) off;
            outPos += 2;
            inPos += len;
            hashTable[hash(get3Bytes(in, inPos))] = inPos;
            ++inPos;
            hashTable[hash(get3Bytes(in, inPos))] = inPos;
            ++inPos;
        }
        // try offlining the tail
        return handleTail(in, inPos, inEnd+4, out, outPos, literals);
    }
    */
}
