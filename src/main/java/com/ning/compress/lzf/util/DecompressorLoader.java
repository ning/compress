package com.ning.compress.lzf.util;

import com.ning.compress.lzf.LZFDecompressor;
import com.ning.compress.lzf.impl.LZFDecompressorVanilla;
import com.ning.compress.lzf.impl.LZFDecompressorWithUnsafe;

/**
 * Simple helper class used for loading the best available
 * {@link LZFDecoder} implementation.
 *<p>
 * Yes, it looks butt-ugly, but does the job. Nonetheless, if anyone
 * has lipstick for this pig, let me know.
 * 
 * @since 0.9
 */
public class DecompressorLoader
{
    private final static DecompressorLoader _instance;
    static {
        Class<?> impl = null;
        try {
            // first, try loading optimal one, which uses Sun JDK Unsafe...
            impl = (Class<?>) Class.forName(LZFDecompressorWithUnsafe.class.getName());
        } catch (Throwable t) { }
        if (impl == null) {
            impl = LZFDecompressorVanilla.class;
        }
        _instance = new DecompressorLoader(impl);
    }

    private final Class<? extends LZFDecompressor> _implClass;
    
    @SuppressWarnings("unchecked")
    private DecompressorLoader(Class<?> imp)
    {
        _implClass = (Class<? extends LZFDecompressor>) imp;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////
     */
    
    /**
     * Method to use for getting decompressor instance that uses the most optimal
     * available methods for underlying data access. It should be safe to call
     * this method as implementations are dynamically loaded; however, on some
     * non-standard platforms it may be necessary to either directly load
     * instances, or use {@link #safeInstance()}.
     */
    public static LZFDecompressor optimalInstance() {
        try {
            return _instance._implClass.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load a LZFDecompressor instance ("+e.getClass().getName()+"): "
                    +e.getMessage(), e);
        }
    }

    /**
     * Method that can be used to ensure that a "safe" decompressor instance is loaded.
     * Safe here means that it should work on any and all Java platforms.
     */
    public static LZFDecompressor safeInstance() {
        // this will always succeed loading; no need to use dynamic class loading or instantiation
        return new LZFDecompressorVanilla();
    }
}
