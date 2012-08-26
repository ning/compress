package perf;

import java.io.*;

import com.ning.compress.lzf.*;
import com.ning.compress.lzf.util.LZFFileInputStream;
import com.ning.compress.lzf.util.LZFFileOutputStream;

/**
 * Micro-benchmark for testing performance of skip alternatives.
 */
public class ManualSkipComparison
{
    private int size = 0;
    
    private void test(File file, int origSize) throws Exception
    {
        // Let's try to guestimate suitable size... to get to 50 megs to process
        final int REPS = (int) ((double) (50 * 1000 * 1000) / (double) file.length());
        
        System.out.printf("Skipping %d bytes of compressed data, %d reps.\n",
                file.length(), REPS);

        int i = 0;
        while (true) {
            try {  Thread.sleep(100L); } catch (InterruptedException ie) { }
            int round = (i++ % 2);

            String msg;
            boolean lf = (round == 0);

            long msecs;
            
            switch (round) {

            case 0:
                msg = "LZF skip/old";
                msecs = testSkip(REPS, file, false);
                break;
            case 1:
                msg = "LZF skip/NEW";
                msecs = testSkip(REPS, file, true);
                break;
            default:
                throw new Error();
            }
            if (lf) {
                System.out.println();
            }
            System.out.println("Test '"+msg+"' ["+size+" bytes] -> "+msecs+" msecs");
            if (size != origSize) { // sanity check
                throw new Error("Wrong skip count!!!");
            }
        }
    }

    private final long testSkip(int REPS, File file, boolean newSkip) throws Exception
    {
        long start = System.currentTimeMillis();
        long len = -1L;
        
//        final byte[] buffer = new byte[16000];
        
        while (--REPS >= 0) {
            InputStream in = newSkip ? new LZFFileInputStream(file)
                : new LZFInputStream(new FileInputStream(file));
            len = 0;
            long skipped;

            while ((skipped = in.skip(Integer.MAX_VALUE)) >= 0L) {
                len += skipped;
            }
            in.close();
        }
        size = (int) len;
        return System.currentTimeMillis() - start;
    }
    
    public static void main(String[] args) throws Exception
    {
        if (args.length != 1) {
            System.err.println("Usage: java ... [file]");
            System.exit(1);
        }
        File in = new File(args[0]);
        System.out.printf("Reading input, %d bytes...\n", in.length());
        File out = File.createTempFile("skip-perf", ".lzf");
        System.out.printf("(writing as file '%s')\n", out.getPath());
        
        byte[] buffer = new byte[4000];
        int count;
        FileInputStream ins = new FileInputStream(in);
        LZFFileOutputStream outs = new LZFFileOutputStream(out);
        
        while ((count = ins.read(buffer)) > 0) {
            outs.write(buffer, 0, count);
        }
        ins.close();
        outs.close();
        System.out.printf("Compressed as file '%s', %d bytes\n", out.getPath(), out.length());

        new ManualSkipComparison().test(out, (int) in.length());
    }
}
