package com.ning.compress.gzip;
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


import java.lang.ref.SoftReference;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * GZIP-codec-specific "extension" to {@link com.ning.compress.BufferRecycler},
 * used for recycling expensive objects.
 * 
 * @author Tatu Saloranta (tatu.saloranta@iki.fi)
 */
public final class GZIPRecycler
{
    final protected static ThreadLocal<SoftReference<GZIPRecycler>> _recyclerRef
        = new ThreadLocal<SoftReference<GZIPRecycler>>();

    protected Inflater _inflater;

    protected Deflater _deflater;
    
    /**
     * Accessor to get thread-local recycler instance
     */
    public static GZIPRecycler instance()
    {
        SoftReference<GZIPRecycler> ref = _recyclerRef.get();
        GZIPRecycler br = (ref == null) ? null : ref.get();
        if (br == null) {
            br = new GZIPRecycler();
            _recyclerRef.set(new SoftReference<GZIPRecycler>(br));
        }
        return br;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////
     */

    public Deflater allocDeflater()
    {
        Deflater d = _deflater;
        if (d == null) { // important: true means 'dont add zlib header'; gzip has its own
            d = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        } else {
            _deflater = null;
        }
        return d;
    }

    public void releaseDeflater(Deflater d)
    {
        if (d != null) {
            d.reset();
            _deflater = d;
        }
    }
    
    public Inflater allocInflater()
    {
        Inflater i = _inflater;
        if (i == null) { // important: true means 'dont add zlib header'; gzip has its own
            i = new Inflater(true);
        } else {
            _inflater = null;
        }
        return i;
    }

    public void releaseInflater(Inflater i)
    {
        if (i != null) {
            i.reset();
            _inflater = i;
        }
    }
}

