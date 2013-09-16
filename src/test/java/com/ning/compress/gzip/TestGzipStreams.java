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


import java.io.*;
import java.util.zip.*;

import org.junit.Assert;
import org.testng.annotations.Test;

import com.ning.compress.BaseForTests;

public class TestGzipStreams extends BaseForTests
{
    private final static String INPUT_STR = "Some somewhat short text string -- but enough repetition to overcome shortness of input";
    private final static byte[] INPUT_BYTES;
    static {
        try {
            INPUT_BYTES = INPUT_STR.getBytes("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Test
    public void testReusableInputStreams() throws IOException
    {
        // Create known good gzip via JDK
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        GZIPOutputStream comp = new GZIPOutputStream(bytes);
        comp.write(INPUT_BYTES);
        comp.close();
        
        // then decode with 'our' thing, twice:
        byte[] raw = bytes.toByteArray();
        OptimizedGZIPInputStream re = new OptimizedGZIPInputStream(new ByteArrayInputStream(raw));
        byte[] b = _readAll(re);
        Assert.assertArrayEquals(INPUT_BYTES, b);
    }

    @Test
    public void testReusableOutputStreams() throws IOException
    {
        // first use custom stream
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        OptimizedGZIPOutputStream re = new OptimizedGZIPOutputStream(bytes);
        re.write(INPUT_BYTES);
        re.close();
        
        byte[] raw = bytes.toByteArray();
        byte[] b = _readAll(new GZIPInputStream(new ByteArrayInputStream(raw)));
        Assert.assertArrayEquals(INPUT_BYTES, b);
    }

    private byte[] _readAll(InputStream in) throws IOException
    {
        byte[] buffer = new byte[1000];
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1000);
        int count;

        while ((count = in.read(buffer)) > 0) {
            bytes.write(buffer, 0, count);
        }
        in.close();
        return bytes.toByteArray();
    }
}
