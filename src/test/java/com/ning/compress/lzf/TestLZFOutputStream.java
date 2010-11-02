package com.ning.compress.lzf;

import java.io.ByteArrayInputStream;
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
		verifyOutputStream(bos, nonEncodableBytesToWrite);
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
		Assert.assertTrue(bos.toByteArray().length > 10);
		Assert.assertTrue(bos.toByteArray().length < bytesToWrite.length*.5);
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
	
	private void verifyOutputStream(ByteArrayOutputStream bos, byte[] reference) throws Exception
	{
		ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
		LZFInputStream lzfi = new LZFInputStream(bis);
		int val =0;
		int idx = 0;
		while((val = lzfi.read()) != -1)
		{
			int refVal = ((int)reference[idx++]) & 255;
			Assert.assertEquals(refVal, val);
		}
	}
}
