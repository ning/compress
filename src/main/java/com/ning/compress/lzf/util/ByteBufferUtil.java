package com.ning.compress.lzf.util;

import java.nio.ByteBuffer;

/**
 * ByteBufferUtil
 */
public final class ByteBufferUtil {

    private ByteBufferUtil() {
    }

    public static void dataCopy(ByteBuffer src, int srcPos,
                                ByteBuffer dest, int destPos,
                                int length) {
        // mark.
        int markRead = src.position();

        // set new pos.
        src.position(srcPos);

        // read data from srcPos.
        byte[] temp = new byte[length];
        src.get(temp, 0, length);

        // reset pos.
        src.position(markRead);

        // put data into array.
        dest.put(temp, 0, length);
    }

    public static void dataCopy(byte[] src, int srcPos,
                                ByteBuffer dest, int destPos,
                                int length) {

        // put data into array.
        dest.put(src, srcPos, length);
    }
}
