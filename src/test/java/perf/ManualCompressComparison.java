package perf;

import java.io.*;
import java.util.zip.DeflaterOutputStream;

import com.ning.compress.gzip.OptimizedGZIPOutputStream;
import com.ning.compress.lzf.*;

/**
 * Simple manual performance micro-benchmark that compares compress and
 * decompress speeds of this LZF implementation with other codecs.
 */
public class ManualCompressComparison
{
    protected int size = 0;
    
    protected int totalSize;
    protected int REPS;

    // 10 megs per cycle
    protected final static int PER_ITERATION_LENGTH = 10 * 1000 * 1000;

    private ManualCompressComparison(int totalSize)
    {
        this.totalSize = totalSize;
    }

    private void test(String[] names, byte[][] docs, int workSize) throws Exception
    {
        final int DOC_COUNT = docs.length;
        final byte[] WORKSPACE = new byte[totalSize];

        // Let's try to guestimate suitable size... to get to 10 megs to process
        // but, with more docs, give more time
        REPS = Math.max(1, (int) ((double) (PER_ITERATION_LENGTH * Math.sqrt(DOC_COUNT)) / (double) totalSize));

//        final int TYPES = 1;
        final int TYPES = 2;
        final int WARMUP_ROUNDS = 5;
        int roundTotal = 0;
        int roundsDone = 0;
        final long[][] times = new long[DOC_COUNT][];
        for (int i = 0; i < times.length; ++i) {
            times[i] = new long[TYPES];
        }
        
        System.out.printf("Read %d bytes to compress, uncompress; will do %d repetitions, %.1f MB per round\n",
                totalSize, REPS, (REPS * totalSize) / 1000000.0);

        // But first, validate!
        _preValidate(docs);
        int[] msecs = new int[DOC_COUNT];

        for (;; ++roundTotal) {
            try {  Thread.sleep(100L); } catch (InterruptedException ie) { }
            int round = (roundTotal % TYPES);
            String msg;
            long msec;
            
            switch (round) {

            case 0:
                msg = "GZIP compress/stream/NING";
                msec = testGzipCompressNing(docs, msecs);
                break;

            case 1:
                msg = "GZIP compress/stream/JDK";
                msec = testGzipCompressJDK(docs, msecs);
                break;
                
            /*
            case 0:
                msg = "LZF-Unsafe compress/block";
                msec = testLZFUnsafeCompress(docs, WORKSPACE, msecs);
                break;
            case 0:
                msg = "LZF compress/block";
                msec = testLZFSafeCompress(REPS, docs, WORKSPACE, msecs);
                roundDone = true;                
                break;
            case 1:
                msg = "LZF compress/stream";
                msec = testLZFUnsafeCompressStream(docs, msecs);
                break;
                */
            default:
                throw new Error();
            }
            
            boolean roundDone = (round == 1);
            
            // skip first 5 rounds to let results stabilize
            if (roundsDone >= WARMUP_ROUNDS) {
                for (int i = 0; i < DOC_COUNT; ++i) {
                    times[i][round] += msecs[i];
                }
            }
            System.out.printf("Test '%s' [%d bytes] -> %d msecs\n", msg, size, msec);
            if (roundDone) {
                roundDone = false;
                ++roundsDone;
                if ((roundsDone % 3) == 0 && roundsDone > WARMUP_ROUNDS) {
                    _printResults((roundsDone - WARMUP_ROUNDS), names, times);
                }
            }
            if ((roundTotal % 17) == 0) {
                System.out.println("[GC]");
                Thread.sleep(100L);
                System.gc();
                Thread.sleep(100L);
            }
        }
    }

    protected void _printResults(int rounds, String[] names, long[][] timeSets)
    {
        System.out.printf("Averages after %d rounds:", rounds);
        double den = (double) rounds;
        double[] totals = null;
        for (int file = 0; file < names.length; ++file) {
            System.out.printf(" %s(", names[file]);
            long[] times = timeSets[file];
            if (totals == null) {
                totals = new double[times.length];
            }
            for (int i = 0; i < times.length; ++i){
                if (i > 0) {
                    System.out.print('/');
                }
                double msecs = times[i] / den;
                System.out.printf("%.1f", msecs);
                totals[i] += msecs;
            }
            System.out.printf(")");
        }
        System.out.println();
        // then totals
        System.out.printf("  for a total of: ");
        // first, msecs
        for (int i = 0; i < totals.length; ++i) {
            if (i > 0) {
                System.out.print('/');
            }
            double msecs = totals[i];
            System.out.printf("%.1f", msecs);
        }
        System.out.print(" msecs; ");
        // then throughput
        for (int i = 0; i < totals.length; ++i) {
            if (i > 0) {
                System.out.print('/');
            }
            double msecs = totals[i];
            double bytes = (REPS * totalSize);
            // msecs-to-seconds, x1000; bytes to megabytes, /1M
            System.out.printf("%.1f", (bytes / msecs) / 1000.0);
        }
        System.out.println(" MB/s");
    }
    
    protected void _preValidate(byte[][] inputs) throws LZFException
    {
        int index = 0;
        for (byte[] input : inputs) {
            ++index;
            byte[] encoded1 = LZFEncoder.encode(input);
            byte[] encoded2 = LZFEncoder.safeEncode(input);
    
            if (encoded1.length == encoded2.length) {
                for (int i = 0, len = encoded1.length; i < len; ++i) {
                    if (encoded1[i] != encoded2[i]) {
                        throw new IllegalStateException("Compressed contents of entry "+index+"/"+input.length+" differ at "+i+"/"+len);
                    }
                }
            } else {
                // Actually, let's allow some slack...
                int diff = Math.abs(encoded1.length - encoded2.length);
                // 1/256 seems fine (but at least 16)
                int maxDiff = Math.max(16, encoded1.length >> 8);
                if (diff > maxDiff) {
                   throw new IllegalStateException("Compressed contents of entry "+index+"/"+input.length+" differ by more than "+maxDiff+" bytes: expected "+encoded1.length+", got "+encoded2.length);
                }
                System.err.printf("WARN: sizes differ slightly, %d vs %s (old/new)\n", encoded1.length, encoded2.length);
            } 
            // uncompress too
            byte[] output1 = LZFDecoder.decode(encoded1);
            byte[] output2 = LZFDecoder.decode(encoded2);
            if (output1.length != output2.length) {
                throw new IllegalStateException("Uncompressed contents of entry "+index+"/"+input.length+" differ!");
            }
            for (int i = 0, len = output1.length; i < len; ++i) {
                if (output1[i] != output2[i]) {
                    throw new IllegalStateException("Uncompressed contents of entry "+index+"/"+input.length+" differ at "+i+"/"+len);
                }
            }
        }
    }
    
    protected final long testLZFSafeCompress(byte[][] inputs,
            final byte[] WORKSPACE, int[] msecs) throws Exception
    {
        size = 0;
        final long mainStart = System.currentTimeMillis();
        for (int i = 0, len = inputs.length; i < len; ++i) {
            final long start = System.currentTimeMillis();
            int reps = REPS;
            int bytes = 0;
            while (--reps >= 0) {
                final byte[] input = inputs[i];
                bytes = LZFEncoder.safeAppendEncoded(input, 0, input.length, WORKSPACE, 0);
            }
            size += bytes;
            msecs[i] = (int) (System.currentTimeMillis() - start);
        }
        return System.currentTimeMillis() - mainStart;
    }

    protected final long testLZFUnsafeCompress(byte[][] inputs,
            final byte[] WORKSPACE, int[] msecs) throws Exception
    {
        size = 0;
        final long mainStart = System.currentTimeMillis();
        for (int i = 0, len = inputs.length; i < len; ++i) {
            final long start = System.currentTimeMillis();
            int reps = REPS;
            int bytes = 0;
            while (--reps >= 0) {
                final byte[] input = inputs[i];
                bytes = LZFEncoder.appendEncoded(input, 0, input.length, WORKSPACE, 0);
            }
            size += bytes;
            msecs[i] = (int) (System.currentTimeMillis() - start);
        }
        return System.currentTimeMillis() - mainStart;
    }
    
    protected final long testLZFUnsafeCompressStream(byte[][] inputs, int[] msecs)
            throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8000);
        size = 0;
        final long mainStart = System.currentTimeMillis();
        for (int i = 0, len = inputs.length; i < len; ++i) {
            bytes.reset();
            final long start = System.currentTimeMillis();

            int reps = REPS;
            while (--reps >= 0) {
                bytes.reset();
                LZFOutputStream out = new LZFOutputStream(bytes);
                out.write(inputs[i]);
                out.close();
            }                
            size += bytes.size();
            msecs[i] = (int) (System.currentTimeMillis() - start);
        }
        return System.currentTimeMillis() - mainStart;
    }

    protected final long testGzipCompressNing(byte[][] inputs, int[] msecs) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8000);
        size = 0;
        final long mainStart = System.currentTimeMillis();
        for (int i = 0, len = inputs.length; i < len; ++i) {
            bytes.reset();
            final long start = System.currentTimeMillis();

            int reps = REPS;
            while (--reps >= 0) {
                bytes.reset();
                OptimizedGZIPOutputStream out = new OptimizedGZIPOutputStream(bytes);
                out.write(inputs[i]);
                out.close();
            }                
            size += bytes.size();
            msecs[i] = (int) (System.currentTimeMillis() - start);
        }
        return System.currentTimeMillis() - mainStart;
    }

    protected final long testGzipCompressJDK(byte[][] inputs, int[] msecs) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8000);
        size = 0;
        final long mainStart = System.currentTimeMillis();
        for (int i = 0, len = inputs.length; i < len; ++i) {
            bytes.reset();
            final long start = System.currentTimeMillis();

            int reps = REPS;
            while (--reps >= 0) {
                bytes.reset();
                DeflaterOutputStream out = new DeflaterOutputStream(bytes);
                out.write(inputs[i]);
                out.close();
            }                
            size += bytes.size();
            msecs[i] = (int) (System.currentTimeMillis() - start);
        }
        return System.currentTimeMillis() - mainStart;
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length < 1) {
            System.err.println("Usage: java ... [file1] ... [fileN]");
            System.exit(1);
        }
        byte[][] data = new byte[args.length][];
        String[] names = new String[args.length];
        int totalSize = 0;
        int maxSize = 0;
        
        for (int i = 0; i < args.length; ++i) {
            File f = new File(args[i]);
            names[i] = f.getName();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) f.length());
            byte[] buffer = new byte[4000];
            int count;
            FileInputStream in = new FileInputStream(f);
            
            while ((count = in.read(buffer)) > 0) {
                bytes.write(buffer, 0, count);
            }
            in.close();
            data[i] = bytes.toByteArray();
            final int len = data[i].length;
            totalSize += len;
            maxSize = Math.max(maxSize, LZFEncoder.estimateMaxWorkspaceSize(len));
        }
        new ManualCompressComparison(totalSize).test(names, data, maxSize);
    }

    protected final static class BogusOutputStream extends OutputStream
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

        public void reset() { _bytes = 0; }
    }

}
