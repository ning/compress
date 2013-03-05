package perf;

import java.io.*;

import com.ning.compress.lzf.*;
import com.ning.compress.lzf.nuevo.UnsafeLZFEncoder;

/**
 * Simple manual performance micro-benchmark that compares compress and
 * decompress speeds of this LZF implementation with other codecs.
 */
public class ManualCompressComparison
{
    protected int size = 0;
 
    protected byte[] _lzfEncoded;
    
    private void test(byte[] input) throws Exception
    {
        _lzfEncoded = LZFEncoder.encode(input);

        // Let's try to guestimate suitable size... to get to 10 megs to process
        final int REPS = Math.max(1, (int) ((double) (10 * 1000 * 1000) / (double) input.length));

//        final int TYPES = 1;
        final int TYPES = 2;
        final int WARMUP_ROUNDS = 5;
        int i = 0;
        int roundsDone = 0;
        final long[] times = new long[TYPES];
        
        System.out.println("Read "+input.length+" bytes to compress, uncompress; will do "+REPS+" repetitions");

        // But first, validate!
        _preValidate(input);
        
        while (true) {
            try {  Thread.sleep(100L); } catch (InterruptedException ie) { }
            int round = (i++ % TYPES);

            String msg;
            boolean lf = (round == 0);

            long msecs;
            
            switch (round) {

            case 0:
                msg = "LZF-Unsafe compress/block";
                msecs = testLZFUnsafeCompress(REPS, input);
                break;
            case 1:
                msg = "LZF compress/block";
                msecs = testLZFCompress(REPS, input);
                break;
                /*
            case 1:
                msg = "LZF compress/stream";
                msecs = testLZFCompressStream(REPS, input);
                break;
                */
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
                        System.out.printf("Averages after %d rounds (NEW / old): %.1f / %.1f msecs\n",
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

    protected void _preValidate(byte[] input) throws LZFException
    {
        byte[] encoded1 = LZFEncoder.encode(input);
        byte[] encoded2 = UnsafeLZFEncoder.encode(input);

        if (encoded1.length == encoded2.length) {
            for (int i = 0, len = encoded1.length; i < len; ++i) {
                if (encoded1[i] != encoded2[i]) {
                    throw new IllegalStateException("Compressed contents differ at "+i+"/"+len);
                }
            }
        } else {
            // Actually, let's allow some slack...
            int diff = Math.abs(encoded1.length - encoded2.length);
            // 1/256 seems fine (but at least 16)
            int maxDiff = Math.max(16, encoded1.length >> 8);
            if (diff > maxDiff) {
               throw new IllegalStateException("Compressed contents differ by more than "+maxDiff+" bytes: expected "+encoded1.length+", got "+encoded2.length);
            }
            System.err.printf("WARN: sizes differ slightly, %d vs %s (old/new)\n", encoded1.length, encoded2.length);
        } 
        // uncompress too
        byte[] output1 = LZFDecoder.decode(encoded1);
        byte[] output2 = LZFDecoder.decode(encoded2);
        if (output1.length != output2.length) {
            throw new IllegalStateException("Uncompressed contents differ!");
        }
        for (int i = 0, len = output1.length; i < len; ++i) {
            if (output1[i] != output2[i]) {
                throw new IllegalStateException("Uncompressed contents differ at "+i+"/"+len);
            }
        }
    }
    
    protected final long testLZFCompress(int REPS, byte[] input) throws Exception
    {
        long start = System.currentTimeMillis();
        byte[] comp = null;
        while (--REPS >= 0) {
            comp = LZFEncoder.encode(input);
        }
        size = comp.length;
        return System.currentTimeMillis() - start;
    }

    protected final long testLZFUnsafeCompress(int REPS, byte[] input) throws Exception
    {
        long start = System.currentTimeMillis();
        byte[] comp = null;
        while (--REPS >= 0) {
            comp = UnsafeLZFEncoder.encode(input);
        }
        size = comp.length;
        return System.currentTimeMillis() - start;
    }
    
    protected final long testLZFCompressStream(int REPS, byte[] input) throws Exception
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
        new ManualCompressComparison().test(bytes.toByteArray());
    }

    private final static class BogusOutputStream extends OutputStream
    {
        protected int _bytes;
        
        @Override public void write(byte[] buf) { write(buf, 0, buf.length); }
        @Override public void write(byte[] buf, int offset, int len) {
            _bytes += len;
        }

        @Override
        public void write(int b) throws IOException {
            _bytes++;
        }

        public int length() { return _bytes; }
    }

}
