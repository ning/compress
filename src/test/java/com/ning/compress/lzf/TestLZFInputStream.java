package com.ning.compress.lzf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;

import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class TestLZFInputStream 
{
	private static int BUFFER_SIZE = LZFChunk.MAX_CHUNK_LEN * 64;
	private byte[] nonEncodableBytesToWrite = new byte[BUFFER_SIZE];
	private byte[] bytesToWrite = new byte[BUFFER_SIZE];
	private ByteArrayOutputStream nonCompressed;
	private ByteArrayOutputStream compressed;
	
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
		nonCompressed = new ByteArrayOutputStream();
		OutputStream os = new LZFOutputStream(nonCompressed);
		os.write(nonEncodableBytesToWrite);
		os.close();
		
		compressed = new ByteArrayOutputStream();
		os = new LZFOutputStream(compressed);
		os.write(bytesToWrite);
		os.close();
	}
	
	@Test
	public void testDecompressNonEncodableReadByte() throws IOException
	{
		doDecompressReadBlock(nonCompressed.toByteArray(), nonEncodableBytesToWrite);
	}
	
	@Test
	public void testDecompressNonEncodableReadBlock() throws IOException
	{
		doDecompressReadBlock(nonCompressed.toByteArray(), nonEncodableBytesToWrite);
	}
	
	@Test
	public void testDecompressEncodableReadByte() throws IOException
	{
		doDecompressReadBlock(compressed.toByteArray(), bytesToWrite);
	}
	
	@Test
	public void testDecompressEncodableReadBlock() throws IOException
	{
		doDecompressReadBlock(compressed.toByteArray(), bytesToWrite);
	}
	
	public void doDecompressNonEncodableReadByte(byte[] bytes, byte[] reference) throws IOException
	{
		ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		int outputBytes = 0;
		InputStream is = new LZFInputStream(bis);
		int val;
		while((val=is.read()) != -1) {
			byte testVal = (byte)(val & 255);
			Assert.assertTrue(testVal == reference[outputBytes]);
			outputBytes++;
		}
		Assert.assertTrue(outputBytes == reference.length);
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
				Assert.assertTrue(testVal == reference[outputBytes]);
				outputBytes++;
			}
		}
		Assert.assertTrue(outputBytes == reference.length);
	}
}
