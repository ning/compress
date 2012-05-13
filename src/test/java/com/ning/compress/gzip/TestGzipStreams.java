package com.ning.compress.gzip;

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
        ReusableGzipInputStream re = new ReusableGzipInputStream();
        re.initialize(new ByteArrayInputStream(raw));
        byte[] b = _readAll(re);
        Assert.assertArrayEquals(INPUT_BYTES, b);
    }

    @Test
    public void testReusableOutputStreams() throws IOException
    {
        // first use custom stream
        ReusableGzipOutputStream re = new ReusableGzipOutputStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        re.initialize(bytes);
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
