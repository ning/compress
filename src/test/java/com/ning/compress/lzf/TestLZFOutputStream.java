package com.ning.compress.lzf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.ning.compress.BaseForTests;

public class TestLZFOutputStream extends BaseForTests
{
    private static int BUFFER_SIZE = LZFChunk.MAX_CHUNK_LEN * 64;
    private byte[] nonEncodableBytesToWrite;
    private byte[] bytesToWrite;

    @BeforeTest(alwaysRun = true)
    public void setUp() throws Exception
    {
        nonEncodableBytesToWrite = constructUncompressable(BUFFER_SIZE);
        String phrase = "all work and no play make Jack a dull boy";
        bytesToWrite = new byte[BUFFER_SIZE];
        byte[] bytes = phrase.getBytes();
        int cursor = 0;
        while(cursor <= bytesToWrite.length) {
            System.arraycopy(bytes, 0, bytesToWrite, cursor, (bytes.length+cursor < bytesToWrite.length)?bytes.length:bytesToWrite.length-cursor);
            cursor += bytes.length;
        }
    }

    @Test
    public void testUnencodable() throws Exception
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStream os = new LZFOutputStream(bos);
        os.write(nonEncodableBytesToWrite);
        os.close();
        Assert.assertTrue(bos.toByteArray().length > nonEncodableBytesToWrite.length);
        verifyOutputStream(bos, nonEncodableBytesToWrite);
    }

    @Test
    public void testStreaming() throws Exception
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStream os = new LZFOutputStream(bos);
        os.write(bytesToWrite);
        os.close();
        int len = bos.toByteArray().length;
        int max = bytesToWrite.length/2;
        if (len <= 10 || len >= max) {
            Assert.fail("Sanity check: should have 10 < len < "+max+"; len = "+len);
        }
        verifyOutputStream(bos, bytesToWrite);
    }

    @Test
    public void testSingleByte() throws Exception
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStream os = new LZFOutputStream(bos);
        int idx = 0;
        for(; idx < BUFFER_SIZE; idx++) {
            os.write(bytesToWrite[idx]);
            if(idx % 1023 == 0 && idx > BUFFER_SIZE/2) {
                os.flush();
            }
        }
        os.close();
        int len = bos.toByteArray().length;
        int max = bytesToWrite.length/2;
        if (len <= 10 || len >= max) {
            Assert.fail("Sanity check: should have 10 < len < "+max+"; len = "+len);
        }
        verifyOutputStream(bos, bytesToWrite);
    }

    @Test
    public void testPartialBuffer() throws Exception
    {
        int offset = 255;
        int len = 1<<17;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStream os = new LZFOutputStream(bos);
        os.write(bytesToWrite, offset, len);
        os.close();
        Assert.assertTrue(bos.toByteArray().length > 10);
        Assert.assertTrue(bos.toByteArray().length < bytesToWrite.length*.5);
        int bytesToCopy = Math.min(len, bytesToWrite.length);
        byte[] compareBytes = new byte[bytesToCopy];
        System.arraycopy(bytesToWrite, offset, compareBytes, 0, bytesToCopy);
        verifyOutputStream(bos, compareBytes);
    }

    @Test
    public void testEmptyBuffer() throws Exception
    {
        byte[] input = new byte[0];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStream os = new LZFOutputStream(bos);
        os.write(input);
        os.close();
        int len = bos.toByteArray().length;
        if (len != 0) {
            Assert.fail("Sanity check: should have len == 0; len = "+len);
        }
        verifyOutputStream(bos, input);
    }

    private void verifyOutputStream(ByteArrayOutputStream bos, byte[] reference) throws Exception
    {
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        LZFInputStream lzfi = new LZFInputStream(bis);
        int val =0;
        int idx = 0;
        while((val = lzfi.read()) != -1) {
            int refVal = ((int)reference[idx++]) & 255;
            Assert.assertEquals(refVal, val);
        }
        lzfi.close();
    }
}
