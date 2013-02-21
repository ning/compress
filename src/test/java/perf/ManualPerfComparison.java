package perf;

import java.io.*;

import com.ning.compress.lzf.*;
import com.ning.compress.lzf.util.ChunkDecoderFactory;

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
        
//        int i = 0;
        // Let's try to guestimate suitable size... to get to 10 megs to process
        final int REPS = (int) ((double) (10 * 1000 * 1000) / (double) input.length);

        System.out.println("Read "+input.length+" bytes to compress, uncompress; will do "+REPS+" repetitions");

        while (true) {
            try {  Thread.sleep(100L); } catch (InterruptedException ie) { }
//            int round = (i++ % 4);
            int round = 1;

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

        final ChunkDecoder decoder = ChunkDecoderFactory.optimalInstance();

        while (--REPS >= 0) {
            uncomp = decoder.decode(encoded);
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
