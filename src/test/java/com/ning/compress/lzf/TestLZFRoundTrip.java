package com.ning.compress.lzf;

import java.io.*;

import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.ning.compress.lzf.impl.UnsafeChunkDecoder;
import com.ning.compress.lzf.impl.VanillaChunkDecoder;

public class TestLZFRoundTrip
{
    @Test 
    public void testVanillaCodec() throws Exception
    {
        _testUsingBlock(new VanillaChunkDecoder());
        _testUsingReader(new VanillaChunkDecoder());
    }

    @Test 
    public void testUnsafeCodec() throws IOException
    {
        _testUsingBlock(new UnsafeChunkDecoder());
        _testUsingReader(new UnsafeChunkDecoder());
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper method
    ///////////////////////////////////////////////////////////////////////
     */


    protected void _testUsingBlock(ChunkDecoder decoder) throws IOException
    {
        byte[] data = readAll(new FileInputStream("src/test/java/shakespeare.tar"));
        byte[] lzf = LZFEncoder.encode(data);
        byte[] decoded = decoder.decode(lzf);
        
        Assert.assertEquals(decoded.length,  data.length);
        Assert.assertEquals(decoded,  data);
    }

    protected void _testUsingReader(ChunkDecoder decoder) throws IOException
    {
        byte[] data = readAll(new FileInputStream("src/test/java/shakespeare.tar"));
        byte[] lzf = LZFEncoder.encode(data);
        LZFInputStream comp = new LZFInputStream(new ByteArrayInputStream(lzf), false, decoder);
        byte[] decoded = readAll(comp);

        Assert.assertEquals(decoded.length,  data.length);
        Assert.assertEquals(decoded,  data);
    }
    
    protected byte[] readResource(String name) throws IOException
    {
        return readAll(getClass().getResourceAsStream(name));
    }

    protected byte[] readAll(InputStream in) throws IOException
    {
        Assert.assertNotNull(in);
        byte[] buffer = new byte[4000];
        int count;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4000);

        while ((count = in.read(buffer)) > 0) {
            bytes.write(buffer, 0, count);
        }
        in.close();
        return bytes.toByteArray();
    }
}
