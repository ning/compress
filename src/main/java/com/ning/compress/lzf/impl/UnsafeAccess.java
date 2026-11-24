package com.ning.compress.lzf.impl;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Provides access to {@code sun.misc.Unsafe}. <b>Internal class.</b>
 *
 * <p>Callers of this class should only check {@link #isUnsafeSupported()} and access {@code Unsafe}
 * when it is actually used, so that users on newer JDKs where memory access through {@code Unsafe}
 * is deprecated (see also <a href="https://openjdk.org/jeps/498">JEP 498</a>) don't receive any
 * warnings when they don't use the {@code Unsafe}-based implementation.
 */
@SuppressWarnings("restriction")
public class UnsafeAccess {
    // Separate nested class to delay loading of `Unsafe` (and potentially triggering a `LinkageError`)
    // And to make it package-private, preventing users from accessing it
    static class Access {
        static final Unsafe unsafe;

        static {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                unsafe = (Unsafe) theUnsafe.get(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        static final long BYTE_ARRAY_OFFSET = Unsafe.ARRAY_BYTE_BASE_OFFSET;
    }

    private static volatile Boolean unsafeSupported = null;

    public static boolean isUnsafeSupported() {
        Boolean unsafeSupported = UnsafeAccess.unsafeSupported;
        if (unsafeSupported != null) {
            return unsafeSupported;
        }

        try {
            Unsafe unsafe = Access.unsafe;
            byte[] array = { 1 };
            unsafeSupported = unsafe.getByte(array, Access.BYTE_ARRAY_OFFSET) == array[0];
        } catch (RuntimeException | LinkageError e) {
            unsafeSupported = false;
        }
        // Tolerate concurrent initialization by multiple threads
        UnsafeAccess.unsafeSupported = unsafeSupported;
        return unsafeSupported;
    }
}
