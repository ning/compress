package com.ning.compress.lzf;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.security.SecureRandom;

import com.ning.compress.BaseForTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestLZFInputStream extends BaseForTests
{
    private static final int BUFFER_SIZE = LZFChunk.MAX_CHUNK_LEN * 64;
    private final byte[] nonEncodableBytesToWrite = new byte[BUFFER_SIZE];
    private final byte[] bytesToWrite = new byte[BUFFER_SIZE];
    private byte[] nonCompressableBytes;
    private final int compressableInputLength = BUFFER_SIZE;
    private byte[] compressedBytes;
	
    @BeforeEach
    public void setUp() throws Exception 
    {
		SecureRandom.getInstance("SHA1PRNG").nextBytes(nonEncodableBytesToWrite);
		String phrase = "all work and no play make Jack a dull boy";
		byte[] bytes = phrase.getBytes(StandardCharsets.UTF_8);
		int cursor = 0;
		while(cursor <= bytesToWrite.length) {
			System.arraycopy(bytes, 0, bytesToWrite, cursor, (bytes.length+cursor < bytesToWrite.length)?bytes.length:bytesToWrite.length-cursor);
			cursor += bytes.length;
		}
        ByteArrayOutputStream nonCompressed = new ByteArrayOutputStream();
		OutputStream os = new LZFOutputStream(nonCompressed);
		os.write(nonEncodableBytesToWrite);
		os.close();
		nonCompressableBytes = nonCompressed.toByteArray();
		
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		os = new LZFOutputStream(compressed);
		os.write(bytesToWrite);
		os.close();
		compressedBytes = compressed.toByteArray();
    }

    @Test
    public void testDecompressNonEncodableReadByte() throws IOException {
        doDecompressReadByte(nonCompressableBytes, nonEncodableBytesToWrite);
    }
	
    @Test
    public void testDecompressNonEncodableReadBlock() throws IOException {
        doDecompressReadBlock(nonCompressableBytes, nonEncodableBytesToWrite);
    }
	
    @Test
    public void testDecompressEncodableReadByte() throws IOException {
        doDecompressReadByte(compressedBytes, bytesToWrite);
    }

    @Test
    public void testDecompressEncodableReadBlock() throws IOException {
        doDecompressReadBlock(compressedBytes, bytesToWrite);
    }

    @Test
    public void testRead0() throws IOException
    {
		ByteArrayInputStream bis = new ByteArrayInputStream(compressedBytes);
		InputStream is = new LZFInputStream(bis);
		assertEquals(0, is.available());
		byte[] buffer = new byte[65536+23];
		int val = is.read(buffer, 0, 0);
		// read of 0 or less should return a 0-byte read.
		assertEquals(0, val);
		val = is.read(buffer, 0, -1);
		assertEquals(0, val);
		// close should work.
		is.close();
    }

    @Test
    public void testAvailable() throws IOException
    {
        ByteArrayInputStream bis = new ByteArrayInputStream(compressedBytes);
        LZFInputStream is = new LZFInputStream(bis);
        assertSame(bis, is.getUnderlyingInputStream());
        assertEquals(0, is.available());
        // read one byte; should decode bunch more, make available
        assertNotEquals(-1, is.read());
        int total = 1; // since we read one byte already
        assertEquals(65534, is.available());
        // and after we skip through all of it, end with -1 for EOF
        long count;
        while ((count = is.skip(16384L)) > 0L) {
            total += (int) count;
        }
        // nothing more available; but we haven't yet closed so:
        assertEquals(0, is.available());
        // and then we close it:
        is.close();
        assertEquals(0, is.available());
        assertEquals(compressableInputLength, total);
    }

    @Test void testIncrementalWithFullReads() throws IOException {
        doTestIncremental(true);
    }

    @Test void testIncrementalWithMinimalReads() throws IOException {
        doTestIncremental(false);
    }

    @Test 
    public void testReadAndWrite() throws Exception
    {
        byte[] fluff = constructFluff(132000);
        byte[] comp = LZFEncoder.encode(fluff);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(fluff.length);
        LZFInputStream in = new LZFInputStream(new ByteArrayInputStream(comp));
        in.readAndWrite(bytes);
        in.close();
        byte[] actual = bytes.toByteArray();
        assertArrayEquals(fluff, actual);
    }

    // Mostly for [Issue#19]
    @Test
    public void testLongSkips() throws Exception
    {
        // 64k per block, 200k gives 3 full, one small
        byte[] fluff = constructFluff(200000);
        byte[] comp = LZFEncoder.encode(fluff);

        // we get about 200k, maybe byte or two more, so:
        final int LENGTH = fluff.length;
        
        LZFInputStream in = new LZFInputStream(new ByteArrayInputStream(comp));
        // read one byte for fun
        assertEquals(fluff[0] & 0xFF, in.read());
        // then skip all but one
        long amt = in.skip(LENGTH-2);
        assertEquals(LENGTH-2, amt);
        assertEquals(fluff[LENGTH-1] & 0xFF, in.read());
        
        assertEquals(-1, in.read());
        in.close();
    }
    
    /*
    ///////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////
    */

    /**
     * Test that creates a longer piece of content, compresses it, and reads
     * back in arbitrary small reads.
     */
    private void doTestIncremental(boolean fullReads) throws IOException
    {
	    // first need to compress something...
	    String[] words = new String[] { "what", "ever", "some", "other", "words", "too" };
	    StringBuilder sb = new StringBuilder(258000);
	    Random rnd = new Random(123);
	    while (sb.length() < 256000) {
	        int i = (rnd.nextInt() & 31);
	        if (i < words.length) {
	            sb.append(words[i]);
	        } else {
	            sb.append(i);
	        }
	    }
	    byte[] uncomp = sb.toString().getBytes(StandardCharsets.UTF_8);
	    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	    LZFOutputStream lzOut = new LZFOutputStream(bytes);
	    lzOut.write(uncomp);
	    lzOut.close();
	    byte[] comp = bytes.toByteArray();

	    // read back, in chunks
            bytes = new ByteArrayOutputStream(uncomp.length);
            byte[] buffer = new byte[500];
            LZFInputStream lzIn = new LZFInputStream(new ByteArrayInputStream(comp), fullReads);
            int pos = 0;
            
            while (true) {
                int len = 1 + ((rnd.nextInt() & 0x7FFFFFFF) % buffer.length);
                int offset = buffer.length - len;

                int count = lzIn.read(buffer, offset, len);
                if (count < 0) {
                    break;
                }
                if (count > len) {
                    fail("Requested "+len+" bytes (offset "+offset+", array length "+buffer.length+"), got "+count);
                }
                pos += count;
                // with full reads, ought to get full results
                if (count != len) {
                    if (fullReads) {
                        // Except at the end, with last incomplete chunk
                        if (pos != uncomp.length) {
                            fail("Got partial read (when requested full read!), position "+pos+" (of full "+uncomp.length+")");
                        }
                    }
                }
                bytes.write(buffer, offset, count);
            }
            byte[] result = bytes.toByteArray();
            assertArrayEquals(uncomp, result);
            lzIn.close();
    }

    private void doDecompressReadByte(byte[] bytes, byte[] reference) throws IOException
    {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        InputStream is = new LZFInputStream(bis);
        int i = 0;
        int testVal;
        while((testVal=is.read()) != -1) {
            int rVal = ((int)reference[i]) & 255;
            assertEquals(rVal, testVal);
            ++i;
        }
        is.close();
    }

    private void doDecompressReadBlock(byte[] bytes, byte[] reference) throws IOException
    {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        int outputBytes = 0;
        InputStream is = new LZFInputStream(bis);
        int val;
        byte[] buffer = new byte[65536+23];
        while((val=is.read(buffer)) != -1) {
            for(int i = 0; i < val; i++) {
                byte testVal = buffer[i];
                assertEquals(reference[outputBytes], testVal);
                ++outputBytes;
            }
        }
        assertEquals(reference.length, outputBytes);
        is.close();
    }
}
