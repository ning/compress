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


import com.ning.compress.lzf.ChunkDecoder;
import com.ning.compress.lzf.impl.VanillaChunkDecoder;
import com.ning.compress.lzf.impl.UnsafeChunkDecoder;

/**
 * Simple helper class used for loading
 * {@link ChunkDecoder} implementations, based on criteria
 * such as "fastest available".
 *<p>
 * Yes, it looks butt-ugly, but does the job. Nonetheless, if anyone
 * has lipstick for this pig, let me know.
 * 
 * @since 0.9
 */
public class ChunkDecoderFactory
{
    private final static ChunkDecoderFactory _instance;
    static {
        Class<?> impl = null;
        try {
            // first, try loading optimal one, which uses Sun JDK Unsafe...
            impl = (Class<?>) Class.forName(UnsafeChunkDecoder.class.getName());
        } catch (Throwable t) { }
        if (impl == null) {
            impl = VanillaChunkDecoder.class;
        }
        _instance = new ChunkDecoderFactory(impl);
    }

    private final Class<? extends ChunkDecoder> _implClass;
    
    @SuppressWarnings("unchecked")
    private ChunkDecoderFactory(Class<?> imp)
    {
        _implClass = (Class<? extends ChunkDecoder>) imp;
    }

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
        try {
            return _instance._implClass.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load a ChunkDecoder instance ("+e.getClass().getName()+"): "
                    +e.getMessage(), e);
        }
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
