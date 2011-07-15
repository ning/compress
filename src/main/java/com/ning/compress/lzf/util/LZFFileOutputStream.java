package com.ning.compress.lzf.util;

import java.io.*;

import com.ning.compress.lzf.*;

/**
 * Helper class that allows use of LZF compression even if a library requires
 * use of {@link FileOutputStream}.
 *<p>
 * Note that use of this class is not recommended unless you absolutely must
 * use a {@link FileOutputStream} instance; otherwise basic {@link LZFOutputStream}
 * (which uses aggregation for underlying streams) is more appropriate
 * 
 * @since 0.8
 */
public class LZFFileOutputStream extends FileOutputStream
{
    /*
    ///////////////////////////////////////////////////////////////////////
    // Construction
    ///////////////////////////////////////////////////////////////////////
     */

    public LZFFileOutputStream(File file) throws FileNotFoundException {
        super(file);
    }

    public LZFFileOutputStream(File file, boolean append) throws FileNotFoundException {
        super(file, append);
    }

    public LZFFileOutputStream(FileDescriptor fdObj) {
        super(fdObj);
    }

    public LZFFileOutputStream(String name) throws FileNotFoundException {
        super(name);
    }

    public LZFFileOutputStream(String name, boolean append) throws FileNotFoundException {
        super(name, append);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // FileOutputStream overrides
    ///////////////////////////////////////////////////////////////////////
     */

    public void close() {
    }

    // fine as is: don't override
    // public FileChannel getChannel();

    // final, can't override:
    // public FileDescriptor getFD();

    public void write(byte[] b) {
    }

    public void write(byte[] b, int off, int len) {
    }

    public void write(int b) {
    }
}
