package com.ning.compress;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import com.ning.compress.lzf.LZFDecoder;
import com.ning.compress.lzf.LZFEncoder;
import com.ning.compress.lzf.LZFException;

public class BaseForTests
{
    private final static byte[] ABCD = new byte[] { 'a', 'b', 'c', 'd' };

    protected byte[] constructFluff(int length)
    {
        Random rnd = new Random(length);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(length + 100);
        while (bytes.size() < length) {
            int num = rnd.nextInt();
            switch (num & 3) {
            case 0:
                try {
                    bytes.write(ABCD);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            case 1:
                bytes.write(num);
                break;
            default:
                bytes.write((num >> 3) & 0x7);
                break;
            }
        }
        return bytes.toByteArray();
    }

    protected byte[] constructUncompressable(int length)
    {
        byte[] result = new byte[length];
        Random rnd = new Random(length);
        // SecureRandom is "more random", but not reproduceable, so use default instead:
//        SecureRandom.getInstance("SHA1PRNG").nextBytes(result);
        rnd.nextBytes(result);
        return result;
    }

    protected byte[] readAll(InputStream in) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
        byte[] buf = new byte[1024];
        int count;

        while ((count = in.read(buf)) > 0) {
            bytes.write(buf, 0, count);
        }
        in.close();
        return bytes.toByteArray();
    }

    protected byte[] compress(byte[] input) {
        return LZFEncoder.encode(input);
    }

    protected byte[] compress(byte[] input, int offset, int len) {
        return LZFEncoder.encode(input, offset, len);
    }

    protected byte[] uncompress(byte[] input) throws LZFException {
        return LZFDecoder.safeDecode(input);
    }

    protected byte[] uncompress(byte[] input, int offset, int len) throws LZFException {
        return LZFDecoder.safeDecode(input, offset, len);
    }
}
