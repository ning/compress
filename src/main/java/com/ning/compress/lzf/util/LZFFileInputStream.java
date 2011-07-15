package com.ning.compress.lzf.util;

import java.io.*;
import java.nio.channels.FileChannel;

import com.ning.compress.lzf.*;

/**
 * Helper class that allows use of LZF compression even if a library requires
 * use of {@link FileInputStream}.
 *<p>
 * Note that use of this class is not recommended unless you absolutely must
 * use a {@link FileInputStream} instance; otherwise basic {@link LZFInputStream}
 * (which uses aggregation for underlying streams) is more appropriate
 * 
 * @since 0.8
 */
public class LZFFileInputStream
    extends FileInputStream
{
    /*
    ///////////////////////////////////////////////////////////////////////
    // Construction
    ///////////////////////////////////////////////////////////////////////
     */
    
    public LZFFileInputStream(File file) throws FileNotFoundException
    {
        super(file);
    }

    public LZFFileInputStream(FileDescriptor fdObj) {
        super(fdObj);
    }

    public LZFFileInputStream(String name) throws FileNotFoundException {
        super(name);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // FileInputStream overrides
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public int available() {
        return 0;
    }

    @Override
    public void close() {
        
    }

    // fine as is: don't override
    // public FileChannel getChannel();

    // final, can't override:
    //public FileDescriptor getFD();

    @Override
    public int read() {
        return -1;
    }

    @Override
    public int read(byte[] b) {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len)
    {
        return -1;
    }

    @Override
    public long skip(long n)
    {
        return -1;
    }
}
