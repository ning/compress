package com.ning.compress.lzf;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.SecureRandom;

import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class TestLZFOutputStream {

	private static int BUFFER_SIZE = LZFChunk.MAX_CHUNK_LEN * 64;
	private byte[] nonEncodableBytesToWrite = new byte[BUFFER_SIZE];
	private byte[] bytesToWrite = new byte[BUFFER_SIZE];
	
	@BeforeTest(alwaysRun = true)
	public void setUp() throws Exception 
	{
		SecureRandom.getInstance("SHA1PRNG").nextBytes(nonEncodableBytesToWrite);
		String phrase = "all work and no play make Jack a dull boy";
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
	}
	
	@Test
	public void testStreaming() throws Exception
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		OutputStream os = new LZFOutputStream(bos);
		os.write(bytesToWrite);
		os.close();
		Assert.assertTrue(bos.toByteArray().length > 10);
		Assert.assertTrue(bos.toByteArray().length < bytesToWrite.length*.5);
	}
	
	@Test
	public void testSingleByte() throws Exception
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		OutputStream os = new LZFOutputStream(bos);
		for(int idx = 0; idx < BUFFER_SIZE; idx++) {
			os.write(bytesToWrite[idx]);
			if(idx % 1023 == 0) {
				os.flush();
			}
		}
		os.close();
		Assert.assertTrue(bos.toByteArray().length > 10);
		Assert.assertTrue(bos.toByteArray().length < bytesToWrite.length*.5);
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
	}
}
