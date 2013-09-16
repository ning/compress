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

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.compress.BaseForTests;

public class TestLZFCompressingInputStream extends BaseForTests
{
    @Test
    public void testSimpleCompression() throws IOException
    {
        // produce multiple chunks, about 3 here:
        byte[] source = constructFluff(140000);
        LZFCompressingInputStream compIn = new LZFCompressingInputStream(new ByteArrayInputStream(source));
        byte[] comp = readAll(compIn);
        byte[] uncomp = uncompress(comp);
        Assert.assertEquals(uncomp, source);

        // and then check that size is about same as with static methods
        byte[] comp2 = compress(source);
        Assert.assertEquals(comp2.length, comp.length);
    }

    @Test
    public void testSimpleNonCompressed() throws IOException
    {
        // produce two chunks as well
        byte[] source = this.constructUncompressable(89000);
        LZFCompressingInputStream compIn = new LZFCompressingInputStream(new ByteArrayInputStream(source));
        byte[] comp = readAll(compIn);
        // 2 non-compressed chunks with headers:
        Assert.assertEquals(comp.length, 89000 + 5 + 5);
        byte[] uncomp = uncompress(comp);
        Assert.assertEquals(uncomp, source);
    }
}
