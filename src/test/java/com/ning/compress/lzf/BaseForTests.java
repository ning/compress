package com.ning.compress.lzf;

import java.io.*;
import java.util.Random;

public class BaseForTests
{
    private final static byte[] ABCD = new byte[] { 'a', 'b', 'c', 'd' };
    
    protected byte[] constructFluff(int length) throws IOException
    {
        Random rnd = new Random(length);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(length + 100);
        while (bytes.size() < length) {
            int num = rnd.nextInt();
            switch (num & 3) {
            case 0:
                bytes.write(ABCD);
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
}
