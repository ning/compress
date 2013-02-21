package com.ning.compress.lzf;

import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.compress.BaseForTests;

public class TestLZFEncoder extends BaseForTests
{
    @Test
    public void testSizeEstimate()
    {
        int max = LZFEncoder.estimateMaxWorkspaceSize(10000);
        // somewhere between 103 and 105%
        if (max < 10300 || max > 10500) {
            Assert.fail("Expected ratio to be 1010 <= x <= 1050, was: "+max);
        }
    }

    @Test
    public void testCompressableChunksSingle() throws Exception
    {
        byte[] source = constructFluff(55000);
        byte[] buffer = new byte[LZFEncoder.estimateMaxWorkspaceSize(source.length)];
        int compLen = LZFEncoder.appendEncoded(source, 0, source.length, buffer, 0);

        // and make sure we get identical compression
        byte[] bufferAsBlock = Arrays.copyOf(buffer, compLen);
        byte[] asBlockStd = LZFEncoder.encode(source);
        Assert.assertEquals(compLen, asBlockStd.length);
        Assert.assertEquals(bufferAsBlock, asBlockStd);

        // then uncompress, verify
        byte[] uncomp = LZFDecoder.decode(buffer, 0, compLen);

        Assert.assertEquals(uncomp.length, source.length);
        Assert.assertEquals(uncomp, source);
    }

    @Test
    public void testCompressableChunksMulti() throws Exception
    {
        // let's do bit over 256k, to get multiple chunks
        byte[] source = constructFluff(4 * 0xFFFF + 4000);
        byte[] buffer = new byte[LZFEncoder.estimateMaxWorkspaceSize(source.length)];
        int compLen = LZFEncoder.appendEncoded(source, 0, source.length, buffer, 0);

        // and make sure we get identical compression
        byte[] bufferAsBlock = Arrays.copyOf(buffer, compLen);
        byte[] asBlockStd = LZFEncoder.encode(source);
        Assert.assertEquals(compLen, asBlockStd.length);
        Assert.assertEquals(bufferAsBlock, asBlockStd);

        // then uncompress, verify
        byte[] uncomp = LZFDecoder.decode(buffer, 0, compLen);

        Assert.assertEquals(uncomp.length, source.length);
        Assert.assertEquals(uncomp, source);
    }

    @Test
    public void testNonCompressableChunksSingle() throws Exception
    {
        byte[] source = constructUncompressable(4000);
        byte[] buffer = new byte[LZFEncoder.estimateMaxWorkspaceSize(source.length)];
        int compLen = LZFEncoder.appendEncoded(source, 0, source.length, buffer, 0);
        
        // and make sure we get identical compression
        byte[] bufferAsBlock = Arrays.copyOf(buffer, compLen);
        byte[] asBlockStd = LZFEncoder.encode(source);
        Assert.assertEquals(compLen, asBlockStd.length);
        Assert.assertEquals(bufferAsBlock, asBlockStd);

        // then uncompress, verify
        byte[] uncomp = LZFDecoder.decode(buffer, 0, compLen);

        Assert.assertEquals(uncomp.length, source.length);
        Assert.assertEquals(uncomp, source);
    }
}
