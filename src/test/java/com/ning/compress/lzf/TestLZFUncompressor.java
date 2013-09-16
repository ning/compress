package com.ning.compress.lzf;
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
import java.util.Random;

import org.junit.Assert;
import org.testng.annotations.Test;

import com.ning.compress.BaseForTests;
import com.ning.compress.DataHandler;
import com.ning.compress.UncompressorOutputStream;

public class TestLZFUncompressor extends BaseForTests
{
    @Test 
    public void testSimpleSmall1by1() throws IOException
    {
        byte[] fluff = constructFluff(4000);
        byte[] comp = LZFEncoder.encode(fluff);

        Collector co = new Collector();
        LZFUncompressor uncomp = new LZFUncompressor(co);
        for (int i = 0, end = comp.length; i < end; ++i) {
            uncomp.feedCompressedData(comp, i, 1);
        }
        uncomp.complete();
        byte[] result = co.getBytes();
        
        Assert.assertArrayEquals(fluff, result);
    }

    @Test 
    public void testSimpleSmallAsChunk() throws IOException
    {
        byte[] fluff = constructFluff(4000);
        byte[] comp = LZFEncoder.encode(fluff);

        // and then uncompress, first byte by bytes
        Collector co = new Collector();
        LZFUncompressor uncomp = new LZFUncompressor(co);
        uncomp.feedCompressedData(comp, 0, comp.length);
        uncomp.complete();
        byte[] result = co.getBytes();
        Assert.assertArrayEquals(fluff, result);
    }
    
    @Test 
    public void testSimpleBiggerVarLength() throws IOException
    {
        byte[] fluff = constructFluff(190000);
        byte[] comp = LZFEncoder.encode(fluff);

        // and then uncompress with arbitrary-sized blocks...
        Random rnd = new Random(123);
        Collector co = new Collector();
        LZFUncompressor uncomp = new LZFUncompressor(co);
        for (int i = 0, end = comp.length; i < end; ) {
            int size = Math.min(end-i, 1+rnd.nextInt(7));
            uncomp.feedCompressedData(comp, i, size);
            i += size;
        }
        uncomp.complete();
        byte[] result = co.getBytes();
        
        Assert.assertArrayEquals(fluff, result);
    }

    @Test
    public void testSimpleBiggerOneChunk() throws IOException
    {
        byte[] fluff = constructFluff(275000);
        byte[] comp = LZFEncoder.encode(fluff);

        // and then uncompress in one chunk
        Collector co = new Collector();
        LZFUncompressor uncomp = new LZFUncompressor(co);
        uncomp.feedCompressedData(comp, 0, comp.length);
        uncomp.complete();
        byte[] result = co.getBytes();
        
        Assert.assertArrayEquals(fluff, result);
    }

    
    @Test
    public void testSimpleBiggerAsStream() throws IOException
    {
        byte[] fluff = constructFluff(277000);
        byte[] comp = LZFEncoder.encode(fluff);
        Collector co = new Collector();
        UncompressorOutputStream out = new UncompressorOutputStream(new LZFUncompressor(co));
        out.write(comp, 0, comp.length);
        out.close();
        byte[] result = co.getBytes();
        
        Assert.assertArrayEquals(fluff, result);
    }

    private final static class Collector implements DataHandler
    {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public boolean handleData(byte[] buffer, int offset, int len) throws IOException {
            bytes.write(buffer, offset, len);
            return true;
        }
        @Override
        public void allDataHandled() throws IOException { }
        public byte[] getBytes() { return bytes.toByteArray(); }
    }
}
