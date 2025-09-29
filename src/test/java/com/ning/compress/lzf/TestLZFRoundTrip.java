package com.ning.compress.lzf;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import com.ning.compress.lzf.impl.UnsafeChunkDecoder;
import com.ning.compress.lzf.impl.VanillaChunkDecoder;
import com.ning.compress.lzf.util.ChunkEncoderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class TestLZFRoundTrip
{
    private final static String[] FILES = {
        "/shakespeare.tar",
        "/shakespeare/hamlet.xml",
        "/shakespeare/macbeth.xml",
        "/shakespeare/play.dtd",
        "/shakespeare/r_and_j.xml"
        ,"/binary/help.bin"
        ,"/binary/word.doc"
    };

    @TempDir
    Path tempDir;

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
        for (File file : files) {
            if (!file.isDirectory()) {
                testLZFCompressionOnFile(file.toPath());
            } else {
                testLZFCompressionOnDir(file);
            }
        }
    }

    private void testLZFCompressionOnFile(Path file) throws IOException
    {
        final ChunkDecoder decoder = new UnsafeChunkDecoder();
        byte[] buf = new byte[64 * 1024];

        Path compressedFile = Files.createTempFile(tempDir, "test", ".lzf");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file));
            OutputStream out = new LZFOutputStream(new BufferedOutputStream(
                Files.newOutputStream(compressedFile)))) {
            int len;
            while ((len = in.read(buf, 0, buf.length)) >= 0) {
                out.write(buf, 0, len);
            }
        }

        // decompress and verify bytes haven't changed
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file));
            DataInputStream compressedIn = new DataInputStream(new LZFInputStream(decoder,
                    Files.newInputStream(compressedFile), false))) {
            int len;
            while ((len = in.read(buf, 0, buf.length)) >= 0) {
                byte[] buf2 = new byte[len];
                compressedIn.readFully(buf2, 0, len);
                byte[] trimmedBuf = new byte[len];
                System.arraycopy(buf, 0, trimmedBuf, 0, len);
                assertArrayEquals(trimmedBuf, buf2);
            }
            assertEquals(-1, compressedIn.read());
        }
    }

    @Test
    public void testHashCollision() throws IOException
    {
        // this test generates a hash collision: [0,1,153,64] hashes the same as [1,153,64,64]
        // and then leverages the bug s/inPos/0/ to corrupt the array
        // the first array is used to insert a reference from this hash to offset 6
        // and then the hash table is reused and still thinks that there is such a hash at position 6
        // and at position 7, it finds a sequence with the same hash
        // so it inserts a buggy reference
        final byte[] b1 = new byte[] {0,1,2,3,4,(byte)153,64,64,64,9,9,9,9,9,9,9,9,9,9};
        final byte[] b2 = new byte[] {1,(byte)153,0,0,0,0,(byte)153,64,64,64,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
        final int off = 6;

        ChunkEncoder encoder = ChunkEncoderFactory.safeInstance();
        ChunkDecoder decoder = new VanillaChunkDecoder();
        _testCollision(encoder, decoder, b1, 0, b1.length);
        _testCollision(encoder, decoder, b2, off, b2.length - off);

        encoder = ChunkEncoderFactory.optimalInstance();
        decoder = new UnsafeChunkDecoder();
        _testCollision(encoder, decoder, b1, 0, b1.length);
        _testCollision(encoder, decoder, b2, off, b2.length - off);
   }

   private void _testCollision(ChunkEncoder encoder, ChunkDecoder decoder, byte[] bytes, int offset, int length) throws IOException
   {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] expected = new byte[length];
        byte[] buffer = new byte[LZFChunk.MAX_CHUNK_LEN];
        byte[] output = new byte[length];
        System.arraycopy(bytes, offset, expected, 0, length);
        encoder.encodeAndWriteChunk(bytes, offset, length, outputStream);
        InputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        assertEquals(length, decoder.decodeChunk(inputStream, buffer, output));
        assertArrayEquals(expected, output);
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

            assertArrayEquals(data, decoded,
            		String.format("File '%s', %d->%d bytes", name, data.length, lzf.length));
        }
    }

    protected void _testUsingReader(ChunkDecoder decoder) throws IOException
    {
        for (String name : FILES) {
            byte[] data = readResource(name);
            byte[] lzf = LZFEncoder.encode(data);
            LZFInputStream comp = new LZFInputStream(decoder, new ByteArrayInputStream(lzf), false);
            byte[] decoded = readAll(comp);

            assertArrayEquals(data, decoded);
        }
    }

    protected byte[] readResource(String name) throws IOException
    {
        return readAll(getClass().getResourceAsStream(name));
    }

    protected byte[] readAll(InputStream in) throws IOException
    {
        assertNotNull(in);
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
