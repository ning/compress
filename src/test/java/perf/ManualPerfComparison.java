package perf;

import java.io.*;

import com.ning.compress.lzf.*;

/**
 * Simple manual performance micro-benchmark that compares compress and
 * decompress speeds of this LZF implementation with other codecs.
 */
public class ManualPerfComparison
{
    private int size = 0;
 
    private byte[] _lzfEncoded;
    
    private void test(byte[] input) throws Exception
    {
        _lzfEncoded = LZFEncoder.encode(input);

        int i = 0;
        // Let's try to guestimate suitable size... to get to 7 megs to process
        final int REPS = (int) ((double) (7 * 1000 * 1000) / (double) input.length);

        System.out.println("Read "+input.length+" bytes to compress, uncompress; will do "+REPS+" repetitions");

        while (true) {
            try {  Thread.sleep(100L); } catch (InterruptedException ie) { }
//            int round = (i++ % 4);
            int round = 3;

            String msg;
            boolean lf = (round == 0);

            long msecs;
            
            switch (round) {

            case 0:
                msg = "LZF compress/block";
                msecs = testLZFCompress(REPS, input);
                break;
            case 1:
                msg = "LZF compress/stream";
                msecs = testLZFCompressStream(REPS, input);
                break;
            case 2:
                msg = "LZF decompress/block";
                msecs = testLZFDecompress(REPS, _lzfEncoded);
                break;
            case 3:
                msg = "LZF decompress/stream";
                msecs = testLZFDecompressStream(REPS, _lzfEncoded);
                break;
            case 4:
                msg = "QuickLZ compress";
                msecs = testQuickLZCompress(REPS, input);
                break;
            case 5:
                msg = "QuickLZ decompress"; // byte
                msecs = testQuickLZDecompress(REPS, input);
                break;
            default:
                throw new Error();
            }
            
            if (lf) {
                System.out.println();
            }
            System.out.println("Test '"+msg+"' ["+size+" bytes] -> "+msecs+" msecs");
        }
    }

    private final long testLZFCompress(int REPS, byte[] input) throws Exception
    {
        long start = System.currentTimeMillis();
        byte[] comp = null;
        while (--REPS >= 0) {
            comp = LZFEncoder.encode(input);
        }
        size = comp.length;
        return System.currentTimeMillis() - start;
    }

    private final long testLZFCompressStream(int REPS, byte[] input) throws Exception
    {
        long start = System.currentTimeMillis();
        while (--REPS >= 0) {
            BogusOutputStream bogus = new BogusOutputStream();
            LZFOutputStream out = new LZFOutputStream(bogus);
            out.write(input);
            out.close();
            size = bogus.length();
        }
        return System.currentTimeMillis() - start;
    }
    
    private final long testLZFDecompress(int REPS, byte[] encoded) throws Exception
    {
        size = encoded.length;
        long start = System.currentTimeMillis();
        byte[] uncomp = null;
        while (--REPS >= 0) {
            uncomp = LZFDecoder.decode(encoded);
        }
        size = uncomp.length;
        return System.currentTimeMillis() - start;
    }

    private final long testLZFDecompressStream(int REPS, byte[] encoded) throws Exception
    {
        final byte[] buffer = new byte[8000];
        size = 0;
        long start = System.currentTimeMillis();
        while (--REPS >= 0) {
            int total = 0;
            LZFInputStream in = new LZFInputStream(new ByteArrayInputStream(encoded));
            int count;
            while ((count = in.read(buffer)) > 0) {
                total += count;
            }
            size = total;
            in.close();
        }
        return System.currentTimeMillis() - start;
    }
    
    private final long testQuickLZCompress(int REPS, byte[] input) throws Exception
    {
        long start = System.currentTimeMillis();
        byte[] comp = null;
        while (--REPS >= 0) {
            comp = QuickLZ.compress(input, 1);
        }
        size = comp.length;
        return System.currentTimeMillis() - start;
    }

    private final long testQuickLZDecompress(int REPS, byte[] input) throws Exception
    {
        final byte[] encoded = QuickLZ.compress(input, 1);
        long start = System.currentTimeMillis();
        byte[] uncomp = null;
        while (--REPS >= 0) {
            uncomp = QuickLZ.decompress(encoded);
        }
        size = uncomp.length;
        return System.currentTimeMillis() - start;
    }
    
    public static void main(String[] args) throws Exception
    {
        if (args.length != 1) {
            System.err.println("Usage: java ... [file]");
            System.exit(1);
        }
        File f = new File(args[0]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) f.length());
        byte[] buffer = new byte[4000];
        int count;
        FileInputStream in = new FileInputStream(f);
        
        while ((count = in.read(buffer)) > 0) {
            bytes.write(buffer, 0, count);
        }
        in.close();
        new ManualPerfComparison().test(bytes.toByteArray());
    }

    final static class BogusOutputStream extends OutputStream
    {
        protected int _bytes;
        
        public void write(byte[] buf) { write(buf, 0, buf.length); }
        public void write(byte[] buf, int offset, int len) {
            _bytes += len;
        }

        @Override
        public void write(int b) throws IOException {
            _bytes++;
        }

        public int length() { return _bytes; }
    }
    
    /*
     * Embedded version of QuickLZ (1.5), from http://www.quicklz.com/
     */
    @SuppressWarnings("unused")
    private final static class QuickLZ
    {
            // Streaming mode not supported
            public final static int QLZ_STREAMING_BUFFER = 0;

            // Bounds checking not supported. Use try...catch instead
            public final static int QLZ_MEMORY_SAFE = 0;

            public final static int QLZ_VERSION_MAJOR = 1;
            public final static int QLZ_VERSION_MINOR = 5;
            public final static int QLZ_VERSION_REVISION = 0;

            // Decrease QLZ_POINTERS_3 to increase compression speed of level 3. Do not
            // edit any other constants!
            private final static int HASH_VALUES = 4096;
            private final static int MINOFFSET = 2;
            private final static int UNCONDITIONAL_MATCHLEN = 6;
            private final static int UNCOMPRESSED_END = 4;
            private final static int CWORD_LEN = 4;
            private final static int DEFAULT_HEADERLEN = 9;
            private final static int QLZ_POINTERS_1 = 1;
            private final static int QLZ_POINTERS_3 = 16;

            static int headerLen(byte[] source)
            {
                    return ((source[0] & 2) == 2) ? 9 : 3;
            }

            static public long sizeDecompressed(byte[] source)
            {
                    if (headerLen(source) == 9)
                            return fast_read(source, 5, 4);
                    else
                            return fast_read(source, 2, 1);
            }

            static public long sizeCompressed(byte[] source)
            {
                    if (headerLen(source) == 9)
                            return fast_read(source, 1, 4);
                    else
                            return fast_read(source, 1, 1);
            }

            private static void write_header(byte[] dst, int level, boolean compressible, int size_compressed, int size_decompressed)
            {
                    dst[0] = (byte)(2 | (compressible ? 1 : 0));
                    dst[0] |= (byte)(level << 2);
                    dst[0] |= (1 << 6);
                    dst[0] |= (0 << 4);
                    fast_write(dst, 1, size_decompressed, 4);
                    fast_write(dst, 5, size_compressed, 4);
            }

            public static byte[] compress(byte[] source, int level)
            {
                    int src = 0;
                    int dst = DEFAULT_HEADERLEN + CWORD_LEN;
                    long cword_val = 0x80000000L;
                    int cword_ptr = DEFAULT_HEADERLEN;
                    byte[] destination = new byte[source.length + 400];
                    int[][] hashtable;
                    int[] cachetable = new int[HASH_VALUES];
                    byte[] hash_counter = new byte[HASH_VALUES];
                    byte[] d2;
                    int fetch = 0;
                    int last_matchstart = (source.length - UNCONDITIONAL_MATCHLEN - UNCOMPRESSED_END - 1);
                    int lits = 0;

                    if (level != 1 && level != 3)
                            throw new RuntimeException("Java version only supports level 1 and 3");

                    if (level == 1)
                            hashtable = new int[HASH_VALUES][QLZ_POINTERS_1];
                    else
                            hashtable = new int[HASH_VALUES][QLZ_POINTERS_3];

                    if (source.length == 0)
                            return new byte[0];

                    if (src <= last_matchstart)
                            fetch = (int)fast_read(source, src, 3);

                    while (src <= last_matchstart)
                    {
                            if ((cword_val & 1) == 1)
                            {
                                    if (src > 3 * (source.length >> 2) && dst > src - (src >> 5))
                                    {
                                            d2 = new byte[source.length+DEFAULT_HEADERLEN];
                                            write_header(d2, level, false, source.length, source.length + DEFAULT_HEADERLEN);
                                            System.arraycopy(source, 0, d2, DEFAULT_HEADERLEN, source.length);
                                            return d2;
                                    }

                                    fast_write(destination, cword_ptr, (cword_val >>> 1) | 0x80000000L, 4);
                                    cword_ptr = dst;
                                    dst += CWORD_LEN;
                                    cword_val = 0x80000000L;
                            }

                            if (level == 1)
                            {
                                    int hash = ((fetch >>> 12) ^ fetch) & (HASH_VALUES - 1);
                                    int o = hashtable[hash][0];
                                    int cache = cachetable[hash] ^ fetch;

                                    cachetable[hash] = fetch;
                                    hashtable[hash][0] = src;

                                    if (cache == 0 && hash_counter[hash] != 0 && (src - o > MINOFFSET || (src == o + 1 && lits >= 3 && src > 3 && source[src] == source[src - 3] && source[src] == source[src - 2] && source[src] == source[src - 1] && source[src] == source[src + 1] && source[src] == source[src + 2])))
                                    {
                                            cword_val = ((cword_val >>> 1) | 0x80000000L);
                                            if (source[o + 3] != source[src + 3])
                                            {
                                                    int f = 3 - 2 | (hash << 4);
                                                    destination[dst + 0] = (byte)(f >>> 0 * 8);
                                                    destination[dst + 1] = (byte)(f >>> 1 * 8);
                                                    src += 3;
                                                    dst += 2;
                                            }
                                            else
                                            {
                                                    int old_src = src;
                                                    int remaining = ((source.length - UNCOMPRESSED_END - src + 1 - 1) > 255 ? 255 : (source.length - UNCOMPRESSED_END - src + 1 - 1));

                                                    src += 4;
                                                    if (source[o + src - old_src] == source[src])
                                                    {
                                                            src++;
                                                            if (source[o + src - old_src] == source[src])
                                                            {
                                                                    src++;
                                                                    while (source[o + (src - old_src)] == source[src] && (src - old_src) < remaining)
                                                                            src++;
                                                            }
                                                    }

                                                    int matchlen = src - old_src;

                                                    hash <<= 4;
                                                    if (matchlen < 18)
                                                    {
                                                            int f = hash | (matchlen - 2);
                                                            // Neither Java nor C# wants to inline fast_write
                                                            destination[dst + 0] = (byte)(f >>> 0 * 8);
                                                            destination[dst + 1] = (byte)(f >>> 1 * 8);
                                                            dst += 2;
                                                    }
                                                    else
                                                    {
                                                            int f = hash | (matchlen << 16);
                                                            fast_write(destination, dst, f, 3);
                                                            dst += 3;
                                                    }
                                            }
                                            lits = 0;
                                            fetch = (int)fast_read(source, src, 3);
                                    }
                                    else
                                    {
                                            lits++;
                                            hash_counter[hash] = 1;
                                            destination[dst] = source[src];
                                            cword_val = (cword_val >>> 1);
                                            src++;
                                            dst++;
                                            fetch = ((fetch >>> 8) & 0xffff) | ((((int)source[src + 2]) & 0xff) << 16);
                                    }
                            }
                            else
                            { 
                                    fetch = (int)fast_read(source, src, 3);

                                    int o, offset2;
                                    int matchlen, k, m, best_k = 0;
                                    byte c;
                                    int remaining = ((source.length - UNCOMPRESSED_END - src + 1 - 1) > 255 ? 255 : (source.length - UNCOMPRESSED_END - src + 1 - 1));
                                    int hash = ((fetch >>> 12) ^ fetch) & (HASH_VALUES - 1);

                                    c = hash_counter[hash];
                                    matchlen = 0;
                                    offset2 = 0;
                                    for (k = 0; k < QLZ_POINTERS_3 && (c > k || c < 0); k++)
                                    {
                                            o = hashtable[hash][k];
                                            if ((byte)fetch == source[o] && (byte)(fetch >>> 8) == source[o + 1] && (byte)(fetch >>> 16) == source[o + 2] && o < src - MINOFFSET)
                                            {
                                                    m = 3;
                                                    while (source[o + m] == source[src + m] && m < remaining)
                                                            m++;
                                                    if ((m > matchlen) || (m == matchlen && o > offset2))
                                                    {
                                                            offset2 = o;
                                                            matchlen = m;
                                                            best_k = k;
                                                    }
                                            }
                                    }
                                    o = offset2;
                                    hashtable[hash][c & (QLZ_POINTERS_3 - 1)] = src;
                                    c++;
                                    hash_counter[hash] = c;

                                    if (matchlen >= 3 && src - o < 131071)
                                    {
                                            int offset = src - o;
                                            for (int u = 1; u < matchlen; u++)
                                            {
                                                    fetch = (int)fast_read(source, src + u, 3);
                                                    hash = ((fetch >>> 12) ^ fetch) & (HASH_VALUES - 1);
                                                    c = hash_counter[hash]++;
                                                    hashtable[hash][c & (QLZ_POINTERS_3 - 1)] = src + u;
                                            }

                                            src += matchlen;
                                            cword_val = ((cword_val >>> 1) | 0x80000000L);

                                            if (matchlen == 3 && offset <= 63)
                                            {
                                                    fast_write(destination, dst, offset << 2, 1);
                                                    dst++;
                                            }
                                            else if (matchlen == 3 && offset <= 16383)
                                            {
                                                    fast_write(destination, dst, (offset << 2) | 1, 2);
                                                    dst += 2;
                                            }
                                            else if (matchlen <= 18 && offset <= 1023)
                                            {
                                                    fast_write(destination, dst, ((matchlen - 3) << 2) | (offset << 6) | 2, 2);
                                                    dst += 2;
                                            }
                                            else if (matchlen <= 33)
                                            {
                                                    fast_write(destination, dst, ((matchlen - 2) << 2) | (offset << 7) | 3, 3);
                                                    dst += 3;
                                            }
                                            else
                                            {
                                                    fast_write(destination, dst, ((matchlen - 3) << 7) | (offset << 15) | 3, 4);
                                                    dst += 4;
                                            }
                                    }
                                    else
                                    {
                                            destination[dst] = source[src];
                                            cword_val = (cword_val >>> 1);
                                            src++;
                                            dst++;
                                    }
                            }
                    }

                    while (src <= source.length - 1)
                    {
                            if ((cword_val & 1) == 1)
                            {
                                    fast_write(destination, cword_ptr, (long)((cword_val >>> 1) | 0x80000000L), 4);
                                    cword_ptr = dst;
                                    dst += CWORD_LEN;
                                    cword_val = 0x80000000L;
                            }

                            destination[dst] = source[src];
                            src++;
                            dst++;
                            cword_val = (cword_val >>> 1);
                    }
                    while ((cword_val & 1) != 1)
                    {
                            cword_val = (cword_val >>> 1);
                    }
                    fast_write(destination, cword_ptr, (long)((cword_val >>> 1) | 0x80000000L), CWORD_LEN);
                    write_header(destination, level, true, source.length, dst);

                    d2 = new byte[dst];
                    System.arraycopy(destination, 0, d2, 0, dst);
                    return d2;
            }

            static long fast_read(byte[] a, int i, int numbytes)
            {
                    long l = 0;
                    for (int j = 0; j < numbytes; j++)
                            l |= ((((int)a[i + j]) & 0xffL) << j * 8);
                    return l;
            }

            static void fast_write(byte[] a, int i, long value, int numbytes)
            {
                    for (int j = 0; j < numbytes; j++)
                            a[i + j] = (byte)(value >>> (j * 8));
            }

            static public byte[] decompress(byte[] source)
            {
                    int size = (int)sizeDecompressed(source);
                    int src = headerLen(source);
                    int dst = 0;
                    long cword_val = 1;
                    byte[] destination = new byte[size];
                    int[] hashtable = new int[4096];
                    byte[] hash_counter = new byte[4096];
                    int last_matchstart = size - UNCONDITIONAL_MATCHLEN - UNCOMPRESSED_END - 1;
                    int last_hashed = -1;
                    int hash;
                    int fetch = 0;

                    int level = (source[0] >>> 2) & 0x3;

                    if (level != 1 && level != 3)
                            throw new RuntimeException("Java version only supports level 1 and 3");

                    if ((source[0] & 1) != 1)
                    {
                            byte[] d2 = new byte[size];
                            System.arraycopy(source, headerLen(source), d2, 0, size);
                            return d2;
                    }

                    for (;;)
                    {
                            if (cword_val == 1)
                            {
                                    cword_val = fast_read(source, src, 4);
                                    src += 4;
                                    if (dst <= last_matchstart)
                                    {
                                            if(level == 1)
                                                    fetch = (int)fast_read(source, src, 3);
                                            else
                                                    fetch = (int)fast_read(source, src, 4);
                                    }
                            }

                            if ((cword_val & 1) == 1)
                            {
                                    int matchlen;
                                    int offset2;

                                    cword_val = cword_val >>> 1;

                                    if (level == 1)
                                    {
                                            hash = (fetch >>> 4) & 0xfff;
                                            offset2 = hashtable[hash];

                                            if ((fetch & 0xf) != 0)
                                            {
                                                    matchlen = (fetch & 0xf) + 2;
                                                    src += 2;
                                            }
                                            else
                                            {
                                                    matchlen = ((int)source[src + 2]) & 0xff;
                                                    src += 3;
                                            }
                                    }
                                    else
                                    {
                                            int offset;

                                            if ((fetch & 3) == 0)
                                            {
                                                    offset = (fetch & 0xff) >>> 2;
                                                    matchlen = 3;
                                                    src++;
                                            }
                                            else if ((fetch & 2) == 0)
                                            {
                                                    offset = (fetch & 0xffff) >>> 2;
                                                    matchlen = 3;
                                                    src += 2;
                                            }
                                            else if ((fetch & 1) == 0)
                                            {
                                                    offset = (fetch & 0xffff) >>> 6;
                                                    matchlen = ((fetch >>> 2) & 15) + 3;
                                                    src += 2;
                                            }
                                            else if ((fetch & 127) != 3)
                                            {
                                                    offset = (fetch >>> 7) & 0x1ffff;
                                                    matchlen = ((fetch >>> 2) & 0x1f) + 2;
                                                    src += 3;
                                            }
                                            else
                                            {
                                                    offset = (fetch >>> 15);
                                                    matchlen = ((fetch >>> 7) & 255) + 3;
                                                    src += 4;
                                            }
                                            offset2 = (int)(dst - offset);
                                    }

                                    destination[dst + 0] = destination[offset2 + 0];
                                    destination[dst + 1] = destination[offset2 + 1];
                                    destination[dst + 2] = destination[offset2 + 2];

                                    for (int i = 3; i < matchlen; i += 1)
                                    {
                                            destination[dst + i] = destination[offset2 + i];
                                    }
                                    dst += matchlen;

                                    if (level == 1)
                                    {
                                            fetch = (int)fast_read(destination, last_hashed + 1, 3); // destination[last_hashed + 1] | (destination[last_hashed + 2] << 8) | (destination[last_hashed + 3] << 16);
                                            while (last_hashed < dst - matchlen)
                                            {
                                                    last_hashed++;
                                                    hash = ((fetch >>> 12) ^ fetch) & (HASH_VALUES - 1);
                                                    hashtable[hash] = last_hashed;
                                                    hash_counter[hash] = 1;
                                                    fetch = fetch >>> 8 & 0xffff | (((int)destination[last_hashed + 3]) & 0xff) << 16;
                                            }
                                            fetch = (int)fast_read(source, src, 3);
                                    }
                                    else
                                    {
                                            fetch = (int)fast_read(source, src, 4);
                                    }
                                    last_hashed = dst - 1;
                            }
                            else
                            {
                                    if (dst <= last_matchstart)
                                    {
                                            destination[dst] = source[src];
                                            dst += 1;
                                            src += 1;
                                            cword_val = cword_val >>> 1;

                                            if (level == 1)
                                            {
                                                    while (last_hashed < dst - 3)
                                                    {
                                                            last_hashed++;
                                                            int fetch2 = (int)fast_read(destination, last_hashed, 3);
                                                            hash = ((fetch2 >>> 12) ^ fetch2) & (HASH_VALUES - 1);
                                                            hashtable[hash] = last_hashed;
                                                            hash_counter[hash] = 1;
                                                    }
                                                    fetch = fetch >> 8 & 0xffff | (((int)source[src + 2]) & 0xff) << 16;
                                            }
                                            else
                                            {
                                                    fetch = fetch >> 8 & 0xffff | (((int)source[src + 2]) & 0xff) << 16 | (((int)source[src + 3]) & 0xff) << 24;
                                            }
                                    }
                                    else
                                    {
                                            while (dst <= size - 1)
                                            {
                                                    if (cword_val == 1)
                                                    {
                                                            src += CWORD_LEN;
                                                            cword_val = 0x80000000L;
                                                    }

                                                    destination[dst] = source[src];
                                                    dst++;
                                                    src++;
                                                    cword_val = cword_val >>> 1;
                                            }
                                            return destination;
                                    }
                            }
                    }
            }
    }


}
