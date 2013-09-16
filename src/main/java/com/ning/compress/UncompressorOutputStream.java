package com.ning.compress;
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


import java.io.*;

/**
 * Simple wrapper or wrapper around {@link Uncompressor}, to help
 * with inter-operability.
 */
public class UncompressorOutputStream extends OutputStream
{
    protected final Uncompressor _uncompressor;

    private byte[] _singleByte = null;
    
    public UncompressorOutputStream(Uncompressor uncomp)
    {
        _uncompressor = uncomp;
    }

    /**
     * Call to this method will result in call to
     * {@link Uncompressor#complete()}, which is idempotent
     * (i.e. can be called multiple times without ill effects).
     */
    @Override
    public void close() throws IOException {
        _uncompressor.complete();
    }

    @Override
    public void flush() { }

    @Override
    public void write(byte[] b) throws IOException {
        _uncompressor.feedCompressedData(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        _uncompressor.feedCompressedData(b, off, len);
    }

    @Override
    public void write(int b)  throws IOException
    {
        if (_singleByte == null) {
            _singleByte = new byte[1];
        }
        _singleByte[0] = (byte) b;
        _uncompressor.feedCompressedData(_singleByte, 0, 1);
    }
    
}
