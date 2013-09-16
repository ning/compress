/* Copyright 2009-2013 Ning, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.ning.compress.lzf;

import java.io.*;

/**
 * Helper class used to store LZF encoded segments (compressed and non-compressed)
 * that can be sequenced to produce LZF files/streams.
 * 
 * @author Tatu Saloranta
 */
public class LZFChunk
{
    /**
     * Maximum length of literal run for LZF encoding.
     */
    public static final int MAX_LITERAL = 1 << 5; // 32

    /**
     *  Chunk length is limited by 2-byte length indicator, to 64k
     */
    public static final int MAX_CHUNK_LEN = 0xFFFF;
    
    /**
     * Header can be either 7 bytes (compressed) or 5 bytes (uncompressed)
     * long
     */
    public static final int MAX_HEADER_LEN = 7;

    public static final int HEADER_LEN_COMPRESSED = 7;
    public static final int HEADER_LEN_NOT_COMPRESSED = 5;
    
    public final static byte BYTE_Z = 'Z';
    public final static byte BYTE_V = 'V';

    public final static int BLOCK_TYPE_NON_COMPRESSED = 0;
    public final static int BLOCK_TYPE_COMPRESSED = 1;

    
    protected final byte[] _data;
    protected LZFChunk _next;

    private LZFChunk(byte[] data) { _data = data; }

    /**
     * Factory method for constructing compressed chunk
     */
    public static LZFChunk createCompressed(int origLen, byte[] encData, int encPtr, int encLen)
    {
        byte[] result = new byte[encLen + HEADER_LEN_COMPRESSED];
        result[0] = BYTE_Z;
        result[1] = BYTE_V;
        result[2] = BLOCK_TYPE_COMPRESSED;
        result[3] = (byte) (encLen >> 8);
        result[4] = (byte) encLen;
        result[5] = (byte) (origLen >> 8);
        result[6] = (byte) origLen;
        System.arraycopy(encData, encPtr, result, HEADER_LEN_COMPRESSED, encLen);
        return new LZFChunk(result);
    }

    public static int appendCompressedHeader(int origLen, int encLen, byte[] headerBuffer, int offset)
    {
        headerBuffer[offset++] = BYTE_Z;
        headerBuffer[offset++] = BYTE_V;
        headerBuffer[offset++] = BLOCK_TYPE_COMPRESSED;
        headerBuffer[offset++] = (byte) (encLen >> 8);
        headerBuffer[offset++] = (byte) encLen;
        headerBuffer[offset++] = (byte) (origLen >> 8);
        headerBuffer[offset++] = (byte) origLen;
        return offset;
    }
    
    public static void writeCompressedHeader(int origLen, int encLen, OutputStream out, byte[] headerBuffer)
        throws IOException
    {
        headerBuffer[0] = BYTE_Z;
        headerBuffer[1] = BYTE_V;
        headerBuffer[2] = BLOCK_TYPE_COMPRESSED;
        headerBuffer[3] = (byte) (encLen >> 8);
        headerBuffer[4] = (byte) encLen;
        headerBuffer[5] = (byte) (origLen >> 8);
        headerBuffer[6] = (byte) origLen;
        out.write(headerBuffer, 0, HEADER_LEN_COMPRESSED);
    }
    
    /**
     * Factory method for constructing compressed chunk
     */
    public static LZFChunk createNonCompressed(byte[] plainData, int ptr, int len)
    {
        byte[] result = new byte[len + HEADER_LEN_NOT_COMPRESSED];
        result[0] = BYTE_Z;
        result[1] = BYTE_V;
        result[2] = BLOCK_TYPE_NON_COMPRESSED;
        result[3] = (byte) (len >> 8);
        result[4] = (byte) len;
        System.arraycopy(plainData, ptr, result, HEADER_LEN_NOT_COMPRESSED, len);
        return new LZFChunk(result);
    }

    /**
     * Method for appending specific content as non-compressed chunk, in
     * given buffer.
     * 
     * @since 0.9.7
     */
    public static int appendNonCompressed(byte[] plainData, int ptr, int len,
            byte[] outputBuffer, int outputPtr)
    {
        outputBuffer[outputPtr++] = BYTE_Z;
        outputBuffer[outputPtr++] = BYTE_V;
        outputBuffer[outputPtr++] = BLOCK_TYPE_NON_COMPRESSED;
        outputBuffer[outputPtr++] = (byte) (len >> 8);
        outputBuffer[outputPtr++] = (byte) len;
        System.arraycopy(plainData, ptr, outputBuffer, outputPtr, len);
        return outputPtr + len;
    }
    
    public static int appendNonCompressedHeader(int len, byte[] headerBuffer, int offset)
    {
        headerBuffer[offset++] = BYTE_Z;
        headerBuffer[offset++] = BYTE_V;
        headerBuffer[offset++] = BLOCK_TYPE_NON_COMPRESSED;
        headerBuffer[offset++] = (byte) (len >> 8);
        headerBuffer[offset++] = (byte) len;
        return offset;
    }
    
    public static void writeNonCompressedHeader(int len, OutputStream out, byte[] headerBuffer)
        throws IOException
    {
        headerBuffer[0] = BYTE_Z;
        headerBuffer[1] = BYTE_V;
        headerBuffer[2] = BLOCK_TYPE_NON_COMPRESSED;
        headerBuffer[3] = (byte) (len >> 8);
        headerBuffer[4] = (byte) len;
        out.write(headerBuffer, 0, HEADER_LEN_NOT_COMPRESSED);
    }
    
    public void setNext(LZFChunk next) { _next = next; }

    public LZFChunk next() { return _next; }
    public int length() { return _data.length; }
    public byte[] getData() { return _data; }

    public int copyTo(byte[] dst, int ptr) {
        int len = _data.length;
        System.arraycopy(_data, 0, dst, ptr, len);
        return ptr+len;
    }
}
