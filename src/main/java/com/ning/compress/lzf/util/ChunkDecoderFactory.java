package com.ning.compress.lzf.util;

import com.ning.compress.lzf.ChunkDecoder;
import com.ning.compress.lzf.impl.UnsafeAccess;
import com.ning.compress.lzf.impl.VanillaChunkDecoder;
import com.ning.compress.lzf.impl.UnsafeChunkDecoder;

/**
 * Simple helper class used for loading
 * {@link ChunkDecoder} implementations, based on criteria
 * such as "fastest available".
 *<p>
 * Yes, it looks butt-ugly, but does the job. Nonetheless, if anyone
 * has lipstick for this pig, let me know.
 */
public class ChunkDecoderFactory
{
    /**
     * @deprecated There is no need to create an instance of this class; use
     *    the static factory methods.
     */
    @Deprecated
    public ChunkDecoderFactory() { }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////
     */
    
    /**
     * Method to use for getting decoder instance that uses the most optimal
     * available methods for underlying data access. It should be safe to call
     * this method as implementations are dynamically loaded; however, on some
     * non-standard platforms it may be necessary to either directly load
     * instances, or use {@link #safeInstance()}.
     */
    public static ChunkDecoder optimalInstance() {
        return UnsafeAccess.isUnsafeSupported()
                ? new UnsafeChunkDecoder()
                : safeInstance();
    }

    /**
     * Method that can be used to ensure that a "safe" decoder instance is loaded.
     * Safe here means that it should work on any and all Java platforms.
     */
    public static ChunkDecoder safeInstance() {
        // this will always succeed loading; no need to use dynamic class loading or instantiation
        return new VanillaChunkDecoder();
    }
}
