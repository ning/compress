package com.ning.compress.lzf.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.ning.compress.BaseForTests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestFileStreams extends BaseForTests
{
    @TempDir
    Path tempDir;

    @Test
    public void testStreams() throws Exception
    {
        File f = tempDir.resolve("lzf-test.lzf").toFile();

        // First, write encoded stuff (won't compress, but produces something)
        byte[] input = "Whatever stuff...".getBytes(StandardCharsets.UTF_8);

        LZFFileOutputStream out = new LZFFileOutputStream(f);
        out.write(input);
        out.close();

        long len = f.length();
        // happens to be 22; 17 bytes uncompressed, with 5 byte header
        assertEquals(22L, len);

        LZFFileInputStream in = new LZFFileInputStream(f);
        for (byte b : input) {
            assertEquals(b & 0xFF, in.read());
        }
        assertEquals(-1, in.read());
        in.close();
    }

    @Test 
    public void testReadAndWrite() throws Exception
    {
        File f = tempDir.resolve("lzf-test.lzf").toFile();

        byte[] fluff = constructFluff(132000);
        LZFFileOutputStream fout = new LZFFileOutputStream(f);
        fout.write(fluff);
        fout.close();
        
        LZFFileInputStream in = new LZFFileInputStream(f);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(fluff.length);
        in.readAndWrite(bytes);
        in.close();
        byte[] actual = bytes.toByteArray();
        assertArrayEquals(fluff, actual);
    }
}
