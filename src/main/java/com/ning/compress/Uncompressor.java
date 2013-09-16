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


import java.io.IOException;

/**
 * Abstract class that defines "push" style API for various uncompressors
 * (aka decompressors or decoders). Implements are alternatives to stream
 * based uncompressors (such as {@link com.ning.compress.lzf.LZFInputStream})
 * in cases where "push" operation is important and/or blocking is not allowed;
 * for example, when handling asynchronous HTTP responses.
 *<p>
 * Note that API does not define the way that listener is attached: this is
 * typically passed through to constructor of the implementation.
 * 
 * @author Tatu Saloranta (tatu.saloranta@iki.fi)
 *
 * @since 0.9.4
 */
public abstract class Uncompressor
{
    /**
     * Method called to feed more compressed data to be uncompressed, and
     * sent to possible listeners.
     *<p>
     * NOTE: return value was added (from void to boolean) in 0.9.9
     * 
     * @return True, if caller should process and feed more data; false if
     *   caller is not interested in more data and processing should be terminated.
     *   (and {@link #complete} should be called immediately)
     */
    public abstract boolean feedCompressedData(byte[] comp, int offset, int len)
        throws IOException;

    /**
     * Method called to indicate that all data to uncompress has already been fed.
     * This typically results in last block of data being uncompressed, and results
     * being sent to listener(s); but may also throw an exception if incomplete
     * block was passed.
     */
    public abstract void complete() throws IOException;
}
