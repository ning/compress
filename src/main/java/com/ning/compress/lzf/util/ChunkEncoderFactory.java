package com.ning.compress.lzf.util;

import com.ning.compress.lzf.*;
import com.ning.compress.lzf.impl.*;

/**
 * Simple helper class used for loading
 * {@link ChunkEncoder} implementations, based on criteria
 * such as "fastest available".
 *<p>
 * As with {@link ChunkDecoderFactory}, is butt-ugly, but does the job.
 * Improvement ideas welcome
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
    public static ChunkEncoder optimalInstance(int totalLength)
    {
        try {
            return UnsafeChunkEncoders.createEncoder(totalLength);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load optimal ChunkEncoder instance ("+e.getClass().getName()+"): "
                    +e.getMessage(), e);
        }
    }

    public static ChunkEncoder optimalNonAllocatingInstance(int totalLength)
    {
        try {
            return UnsafeChunkEncoders.createNonAllocatingEncoder(totalLength);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load optimal ChunkEncoder instance ("+e.getClass().getName()+"): "
                    +e.getMessage(), e);
        }
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

    public static ChunkEncoder safeNonAllocatingInstance(int totalLength) {
        return VanillaChunkEncoder.nonAllocatingEncoder(totalLength);
    }
}
