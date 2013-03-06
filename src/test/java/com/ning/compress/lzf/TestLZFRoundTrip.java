package com.ning.compress.lzf;

import java.io.*;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.compress.lzf.impl.UnsafeChunkDecoder;
import com.ning.compress.lzf.impl.VanillaChunkDecoder;

public class TestLZFRoundTrip
{
    private final static String[] FILES = {
        "/shakespeare.tar",
        "/shakespeare/hamlet.xml",
        "/shakespeare/macbeth.xml",
        "/shakespeare/play.dtd",
        "/shakespeare/r_and_j.xml"
    };
    
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

    @Test 
    public void testLZFCompressionOnTestFiles() throws IOException {
        for (int i = 0; i < 100; i++) {
            testLZFCompressionOnDir(new File("src/test/resources/shakespeare"));
        }
    }

    private void testLZFCompressionOnDir(File dir) throws IOException
    {
        File[] files = dir.listFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (!file.isDirectory()) {
                testLZFCompressionOnFile(file);
            } else {
                testLZFCompressionOnDir(file);
            }
        }
    }

    private void testLZFCompressionOnFile(File file) throws IOException
    {
        final ChunkDecoder decoder = new UnsafeChunkDecoder();
        
        // File compressedFile = createEmptyFile("test.lzf");
        File compressedFile = new File("/tmp/test.lzf");
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        OutputStream out = new LZFOutputStream(new BufferedOutputStream(
                new FileOutputStream(compressedFile)));
        byte[] buf = new byte[64 * 1024];
        int len;
        while ((len = in.read(buf, 0, buf.length)) >= 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();

        // decompress and verify bytes haven't changed
        in = new BufferedInputStream(new FileInputStream(file));
        DataInputStream compressedIn = new DataInputStream(new LZFInputStream(decoder,
                new FileInputStream(compressedFile), false));
        while ((len = in.read(buf, 0, buf.length)) >= 0) {
            byte[] buf2 = new byte[len];
            compressedIn.readFully(buf2, 0, len);
            byte[] trimmedBuf = new byte[len];
            System.arraycopy(buf, 0, trimmedBuf, 0, len);
            Assert.assertEquals(trimmedBuf, buf2);
        }
        Assert.assertEquals(-1, compressedIn.read());
        in.close();
        compressedIn.close();
    }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper method
    ///////////////////////////////////////////////////////////////////////
     */


    protected void _testUsingBlock(ChunkDecoder decoder) throws IOException
    {
        for (String name : FILES) {
            byte[] data = readResource(name);
            byte[] lzf = LZFEncoder.encode(data);
            byte[] decoded = decoder.decode(lzf);

            Assert.assertEquals(decoded.length,  data.length);
            Assert.assertEquals(decoded,  data);
        }
    }

    protected void _testUsingReader(ChunkDecoder decoder) throws IOException
    {
        for (String name : FILES) {
            byte[] data = readResource(name);
            byte[] lzf = LZFEncoder.encode(data);
            LZFInputStream comp = new LZFInputStream(decoder, new ByteArrayInputStream(lzf), false);
            byte[] decoded = readAll(comp);
    
            Assert.assertEquals(decoded.length,  data.length);
            Assert.assertEquals(decoded,  data);
        }
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
