package com.ning.compress.lzf.impl;

import com.ning.compress.BufferRecycler;
import java.lang.reflect.Field;

import sun.misc.Unsafe;

import com.ning.compress.lzf.ChunkEncoder;
import com.ning.compress.lzf.LZFChunk;

/**
 * {@link ChunkEncoder} implementation that handles actual encoding of individual chunks,
 * using Sun's <code>sun.misc.Unsafe</code> functionality, which gives
 * nice extra boost for speed.
 * 
 * @author Tatu Saloranta (tatu.saloranta@iki.fi)
 */
@SuppressWarnings("restriction")
public abstract class UnsafeChunkEncoder
    extends ChunkEncoder
{
    // // Our Nitro Booster, mr. Unsafe!

    static final Unsafe unsafe;
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

    // All members here (fields, constructors, methods) are at most package-private; users are
    // not supposed to subclass this class

    static final long BYTE_ARRAY_OFFSET = unsafe.arrayBaseOffset(byte[].class);

    static final long BYTE_ARRAY_OFFSET_PLUS2 = BYTE_ARRAY_OFFSET + 2;

    UnsafeChunkEncoder(int totalLength) {
        super(totalLength);
    }

    UnsafeChunkEncoder(int totalLength, boolean bogus) {
        super(totalLength, bogus);
    }

    UnsafeChunkEncoder(int totalLength, BufferRecycler bufferRecycler) {
        super(totalLength, bufferRecycler);
    }

    UnsafeChunkEncoder(int totalLength, BufferRecycler bufferRecycler, boolean bogus) {
        super(totalLength, bufferRecycler, bogus);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Shared helper methods
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * @param start start index, inclusive
     * @param end end index, exclusive
     */
    static void _checkArrayIndices(byte[] array, int start, int end) {
        if (start < 0 || end < start || end > array.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    static void _checkOutputLength(int inputLen, int outputLen) {
        int maxEncoded = inputLen + ((inputLen + 31) >> 5);

        if (maxEncoded > outputLen) {
            throw new IllegalArgumentException("Output length " + outputLen + " is too small for input length " + inputLen);
        }
    }

    final static int _copyPartialLiterals(byte[] in, int inPos, byte[] out, int outPos,
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
        int left = (literals & 7);
        if (left > 4) {
            unsafe.putLong(out, rawOutPtr, unsafe.getLong(in, rawInPtr));
        } else {
            unsafe.putInt(out, rawOutPtr, unsafe.getInt(in, rawInPtr));
        }

        return outPos+literals;
    }

    final static int _copyLongLiterals(byte[] in, int inPos, byte[] out, int outPos,
            int literals)
    {
        inPos -= literals;

        long rawInPtr = BYTE_ARRAY_OFFSET + inPos;
        long rawOutPtr = BYTE_ARRAY_OFFSET + outPos;
        
        while (literals >= LZFChunk.MAX_LITERAL) {
            out[outPos++] = (byte) 31;
            ++rawOutPtr;

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
            rawInPtr += 8;
            rawOutPtr += 8;
            
            inPos += LZFChunk.MAX_LITERAL;
            outPos += LZFChunk.MAX_LITERAL;
            literals -= LZFChunk.MAX_LITERAL;
        }
        if (literals > 0) {
            return _copyPartialLiterals(in, inPos+literals, out, outPos, literals);
        }
        return outPos;
    }
    
    final static int _copyFullLiterals(byte[] in, int inPos, byte[] out, int outPos)
    {
        // literals == 32
        out[outPos++] = (byte) 31;

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

    final static int _handleTail(byte[] in, int inPos, int inEnd, byte[] out, int outPos,
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

    final static int _findTailMatchLength(final byte[] in, int ptr1, int ptr2, final int maxPtr1)
    {
        final int start1 = ptr1;
        while (ptr1 < maxPtr1 && in[ptr1] == in[ptr2]) {
            ++ptr1;
            ++ptr2;
        }
        return ptr1 - start1 + 1; // i.e. 
    }
}
