package perf;

import java.io.*;

import com.ning.compress.lzf.*;
import com.ning.compress.lzf.util.ChunkDecoderFactory;

/**
 * Simple manual performance micro-benchmark that compares compress and
 * decompress speeds of this LZF implementation with other codecs.
 */
public class ManualUncompressComparison
{
    protected int size = 0;
 
    protected byte[] _lzfEncoded;
    
    private void test(byte[] input) throws Exception
    {
        _lzfEncoded = LZFEncoder.encode(input);

        // Let's try to guestimate suitable size... to get to 20 megs to process
        final int REPS = Math.max(1, (int) ((double) (20 * 1000 * 1000) / (double) input.length));

//        final int TYPES = 1;
        final int TYPES = 2;
        final int WARMUP_ROUNDS = 5;
        int i = 0;
        int roundsDone = 0;
        final long[] times = new long[TYPES];
        
        System.out.println("Read "+input.length+" bytes to compress, uncompress; will do "+REPS+" repetitions");

        // But first, validate!
        _preValidate(_lzfEncoded);
        
        while (true) {
            try {  Thread.sleep(100L); } catch (InterruptedException ie) { }
            int round = (i++ % TYPES);

            String msg;
            boolean lf = (round == 0);

            long msecs;
            
            switch (round) {

            case 0:
                msg = "LZF decompress/block/safe";
                msecs = testLZFDecompress(REPS, _lzfEncoded, ChunkDecoderFactory.safeInstance());
                break;
            case 1:
                msg = "LZF decompress/block/UNSAFE";
                msecs = testLZFDecompress(REPS, _lzfEncoded, ChunkDecoderFactory.optimalInstance());
                break;
            case 2:
                msg = "LZF decompress/stream";
                msecs = testLZFDecompressStream(REPS, _lzfEncoded);
                break;
            default:
                throw new Error();
            }
            
            // skip first 5 rounds to let results stabilize
            if (roundsDone >= WARMUP_ROUNDS) {
                times[round] += msecs;
            }
            System.out.printf("Test '%s' [%d bytes] -> %d msecs\n", msg, size, msecs);
            if (lf) {
                ++roundsDone;
                if ((roundsDone % 3) == 0 && roundsDone > WARMUP_ROUNDS) {
                    double den = (double) (roundsDone - WARMUP_ROUNDS);
                    if (times.length == 1) {
                        System.out.printf("Averages after %d rounds: %.1f msecs\n",
                                (int) den, times[0] / den);
                    } else {
                        System.out.printf("Averages after %d rounds (safe / UNSAFE): %.1f / %.1f msecs\n",
                                (int) den,
                                times[0] / den, times[1] / den);
                    }
                    System.out.println();
                }
            }
            if ((i % 17) == 0) {
                System.out.println("[GC]");
                Thread.sleep(100L);
                System.gc();
                Thread.sleep(100L);
            }
        }
    }

    protected void _preValidate(byte[] compressed) throws LZFException
    {
        byte[] decoded1 = LZFDecoder.decode(compressed);
        byte[] decoded2 = LZFDecoder.safeDecode(compressed);

        if (decoded1.length == decoded2.length) {
            for (int i = 0, len = decoded1.length; i < len; ++i) {
                if (decoded1[i] != decoded2[i]) {
                    throw new IllegalStateException("Uncompressed contents differ at "+i+"/"+len);
                }
            }
        } else {
        	throw new IllegalStateException("Uncompressed content lengths diff: expected "+decoded1.length+", got "+decoded2.length);
        }
    }

    protected final long testLZFDecompress(int REPS, byte[] encoded, ChunkDecoder decoder) throws Exception
    {
        size = encoded.length;
        long start = System.currentTimeMillis();
        byte[] uncomp = null;

        while (--REPS >= 0) {
            uncomp = decoder.decode(encoded);
        }
        size = uncomp.length;
        return System.currentTimeMillis() - start;
    }

    protected final long testLZFDecompressStream(int REPS, byte[] encoded) throws Exception
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
        new ManualUncompressComparison().test(bytes.toByteArray());
    }
}
