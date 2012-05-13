package com.ning.compress;

import java.io.IOException;

/**
 * Interface used by {@link Uncompressor} implementations: receives
 * uncompressed data and processes it appropriately.
 *
 * @since 0.9.4
 */
public interface DataHandler
{
    public void handleData(byte[] buffer, int offset, int len) throws IOException;
}
