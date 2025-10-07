package com.ning.compress.lzf.impl;

import com.ning.compress.BufferRecycler;
import com.ning.compress.lzf.LZFChunk;

/**
 * Implementation to use on Big-Endian architectures.
 */
@SuppressWarnings("restriction")
public final class UnsafeChunkEncoderBE
    extends UnsafeChunkEncoder
{
    public UnsafeChunkEncoderBE(int totalLength) {
        super(totalLength);
    }

    public UnsafeChunkEncoderBE(int totalLength, boolean bogus) {
        super(totalLength, bogus);
    }
    public UnsafeChunkEncoderBE(int totalLength, BufferRecycler bufferRecycler) {
        super(totalLength, bufferRecycler);
    }

    public UnsafeChunkEncoderBE(int totalLength, BufferRecycler bufferRecycler, boolean bogus) {
        super(totalLength, bufferRecycler, bogus);
    }


    @Override
    protected int tryCompress(byte[] in, int inPos, int inEnd, byte[] out, int outPos)
    {
        // Sanity checks; otherwise if any of the arguments are invalid `Unsafe` might corrupt memory
        _checkArrayIndices(in, inPos, inEnd);
        _checkArrayIndices(out, outPos, out.length);
        _checkOutputLength(inEnd - inPos, out.length - outPos);

        final int[] hashTable = _hashTable;
        int literals = 0;
        inEnd -= TAIL_LENGTH;
        final int firstPos = inPos; // so that we won't have back references across block boundary

        int seen = _getInt(in, inPos) >> 16;
        
        while (inPos < inEnd) {
            seen = (seen << 8) + (in[inPos + 2] & 255);

            int off = hash(seen);
            int ref = hashTable[off];
            hashTable[off] = inPos;

            // First expected common case: no back-ref (for whatever reason)
            if ((ref >= inPos) // can't refer forward (i.e. leftovers)
                    || (ref < firstPos) // or to previous block
                    || (off = inPos - ref) > MAX_OFF
                    || ((seen << 8) != (_getInt(in, ref-1) << 8))) {
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
            seen = _getInt(in, inPos);
            hashTable[hash(seen >> 8)] = inPos;
            ++inPos;
            hashTable[hash(seen)] = inPos;
            ++inPos;
        }
        // try offlining the tail
        return _handleTail(in, inPos, inEnd+TAIL_LENGTH, out, outPos, literals);
    }

    private final static int _getInt(final byte[] in, final int inPos) {
        return unsafe.getInt(in, BYTE_ARRAY_OFFSET + inPos);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Methods for finding length of a back-reference
    ///////////////////////////////////////////////////////////////////////
     */
    
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
                return ptr1 - base + _leadingBytes(l1, l2);
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

    /* With Big-Endian, in-memory layout is "natural", so what we consider
     * leading is also leading for in-register.
     */
    
    private final static int _leadingBytes(int i1, int i2) {
        return Integer.numberOfLeadingZeros(i1 ^ i2) >> 3;
    }
    
    private final static int _leadingBytes(long l1, long l2) {
        return Long.numberOfLeadingZeros(l1 ^ l2) >> 3;
    }
}
