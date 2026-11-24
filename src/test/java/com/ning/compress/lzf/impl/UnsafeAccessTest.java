package com.ning.compress.lzf.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnsafeAccessTest {
    @Test
    void isUnsafeSupported() {
        assertTrue(UnsafeAccess.isUnsafeSupported());
    }

    @Test
    void access() {
        assertNotNull(UnsafeAccess.Access.unsafe);
    }
}
