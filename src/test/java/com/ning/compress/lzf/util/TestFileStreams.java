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


import java.io.*;

import org.testng.annotations.Test;
import org.testng.Assert;

import com.ning.compress.BaseForTests;

public class TestFileStreams extends BaseForTests
{
    @Test 
    public void testStreams() throws Exception
    {
        File f = File.createTempFile("lzf-test", ".lzf");
        f.deleteOnExit();

        // First, write encoded stuff (won't compress, but produces something)
        byte[] input = "Whatever stuff...".getBytes("UTF-8");

        LZFFileOutputStream out = new LZFFileOutputStream(f);
        out.write(input);
        out.close();

        long len = f.length();
        // happens to be 22; 17 bytes uncompressed, with 5 byte header
        Assert.assertEquals(len, 22L);

        LZFFileInputStream in = new LZFFileInputStream(f);
        for (int i = 0; i < input.length; ++i) {
            Assert.assertEquals(in.read(), input[i] & 0xFF);
        }
        Assert.assertEquals(in.read(), -1);
        in.close();
    }

    @Test 
    public void testReadAndWrite() throws Exception
    {
        File f = File.createTempFile("lzf-test", ".lzf");
        f.deleteOnExit();

        byte[] fluff = constructFluff(132000);
        LZFFileOutputStream fout = new LZFFileOutputStream(f);
        fout.write(fluff);
        fout.close();
        
        LZFFileInputStream in = new LZFFileInputStream(f);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(fluff.length);
        in.readAndWrite(bytes);
        in.close();
        byte[] actual = bytes.toByteArray();
        Assert.assertEquals(actual.length, fluff.length);
        Assert.assertEquals(actual, fluff);
    }
}
