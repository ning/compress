package com.ning.compress.lzf.util;
/*
 *
 * Copyright 2009-2013 Ning, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
*/


import com.ning.compress.lzf.ChunkEncoder;
import com.ning.compress.lzf.LZFChunk;
import com.ning.compress.lzf.impl.UnsafeChunkEncoders;
import com.ning.compress.lzf.impl.VanillaChunkEncoder;

/**
 * Simple helper class used for loading
 * {@link ChunkEncoder} implementations, based on criteria
 * such as "fastest available" or "safe to run anywhere".
 *
 * @since 0.9.7
 */
public class ChunkEncoderFactory
{
    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Convenience method, equivalent to:
     *<code>
     *   return optimalInstance(LZFChunk.MAX_CHUNK_LEN);
     *</code>
     */
    public static ChunkEncoder optimalInstance() {
        return optimalInstance(LZFChunk.MAX_CHUNK_LEN);
    }

    /**
     * Method to use for getting compressor instance that uses the most optimal
     * available methods for underlying data access. It should be safe to call
     * this method as implementations are dynamically loaded; however, on some
     * non-standard platforms it may be necessary to either directly load
     * instances, or use {@link #safeInstance}.
     *
     * @param totalLength Expected total length of content to compress; only matters
     *    for content that is smaller than maximum chunk size (64k), to optimize
     *    encoding hash tables
     */
    public static ChunkEncoder optimalInstance(int totalLength) {
        try {
            return UnsafeChunkEncoders.createEncoder(totalLength);
        } catch (Exception e) {
            return safeInstance(totalLength);
        }
    }

    /**
     * Factory method for constructing encoder that is always passed buffer
     * externally, so that it will not (nor need) allocate encoding buffer.
     */
    public static ChunkEncoder optimalNonAllocatingInstance(int totalLength) {
        try {
            return UnsafeChunkEncoders.createNonAllocatingEncoder(totalLength);
        } catch (Exception e) {
            return safeNonAllocatingInstance(totalLength);
        }
    }

    /**
     * Convenience method, equivalent to:
     *<code>
     *   return safeInstance(LZFChunk.MAX_CHUNK_LEN);
     *</code>
     */
    public static ChunkEncoder safeInstance() {
        return safeInstance(LZFChunk.MAX_CHUNK_LEN);
    }
    /**
     * Method that can be used to ensure that a "safe" compressor instance is loaded.
     * Safe here means that it should work on any and all Java platforms.
     *
     * @param totalLength Expected total length of content to compress; only matters
     *    for content that is smaller than maximum chunk size (64k), to optimize
     *    encoding hash tables
     */
    public static ChunkEncoder safeInstance(int totalLength) {
        return new VanillaChunkEncoder(totalLength);
    }

    /**
     * Factory method for constructing encoder that is always passed buffer
     * externally, so that it will not (nor need) allocate encoding buffer.
     */
    public static ChunkEncoder safeNonAllocatingInstance(int totalLength) {
        return VanillaChunkEncoder.nonAllocatingEncoder(totalLength);
    }
}
