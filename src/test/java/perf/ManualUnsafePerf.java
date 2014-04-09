package perf;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class ManualUnsafePerf
{
    protected static final Unsafe unsafe;
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static final long BYTE_ARRAY_OFFSET = unsafe.arrayBaseOffset(byte[].class);

    protected static final long CHAR_ARRAY_OFFSET = unsafe.arrayBaseOffset(char[].class);

    static final int INPUT_LEN = 48;
    
    private void test() throws Exception
    {
        // Let's try to guestimate suitable size... to get to 10 megs to process
        // but, with more docs, give more time
        final int REPS = 2500 * 1000;

        final int WARMUP_ROUNDS = 5;
        int roundTotal = 0;
        int roundsDone = 0;
        final String[] names = new String[] {"Decode/JDK", "Decode/Unsafe" };
        final int TYPES = names.length;
        final long[] times = new long[TYPES];

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < INPUT_LEN; ++i) {
            sb.append((char) ('A'+i));
        }
        byte[] INPUT = new byte[INPUT_LEN + 8];
        {
            byte[] b = sb.toString().getBytes("UTF-8");
            System.arraycopy(b, 0, INPUT, 4, INPUT_LEN);
        }
        
        for (;; ++roundTotal) {
            try {  Thread.sleep(100L); } catch (InterruptedException ie) { }
            int round = (roundTotal % TYPES);
            String msg = names[round];
            long msec;

            switch (round) {
            case 0:
                msec = testDecodeJDK(REPS, INPUT, 4, INPUT_LEN);
                break;
            case 1:
                msec = testDecodeUnsafe(REPS, INPUT, 4, INPUT_LEN);
                break;
            default:
                throw new Error();
            }
            
            boolean roundDone = (round == 1);
            
            // skip first 5 rounds to let results stabilize
            if (roundsDone >= WARMUP_ROUNDS) {
                times[round] += msec;
            }
            System.out.printf("Test '%s' -> %d msecs\n", msg, msec);
            if (roundDone) {
                roundDone = false;
                ++roundsDone;
                if ((roundsDone % 7) == 0 && roundsDone > WARMUP_ROUNDS) {
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

    public long testDecodeJDK(int reps, byte[] input, final int offset, final int len)
    {
        final long mainStart = System.currentTimeMillis();
        char[] result = new char[64];
        while (--reps >= 0) {
            for (int i = 0; i < len; ++i) {
                result[i] = (char) input[offset+i];
            }
        }
        long time = System.currentTimeMillis() - mainStart;
        return time;
    }

    public long testDecodeUnsafe(int reps, byte[] input, final int offset, final int len)
    {
        final long mainStart = System.currentTimeMillis();
        char[] result = new char[100];

        while (--reps >= 0) {
//            long inBase = BYTE_ARRAY_OFFSET + offset;
//            long outBase = CHAR_ARRAY_OFFSET;

//            final long inEnd = inBase + len;
            for (int i = 0; i < len; ) {
                result[i++] = (char) input[offset+1];
                
                /*
                int quad = unsafe.getInt(input, inBase);
                inBase += 4;

                result[i++] = (char) (quad >>> 24);
                result[i++] = (char) ((quad >> 16) & 0xFF);
                result[i++] = (char) ((quad >> 8) & 0xFF);
                result[i++] = (char) (quad & 0xFF);
                */

                /*
                int q1 = ((quad >>> 24) << 16) + ((quad >> 16) & 0xFF);

                unsafe.putInt(result, outBase, q1);
                outBase += 4;

                int q2 = (quad & 0xFFFF);
                q2 = ((q2 >> 8) << 16) | (q2 & 0xFF);

                unsafe.putInt(result, outBase, q2);
                outBase += 4;
                
                long l = q1;
                l = (l << 32) | q2;
                
                unsafe.putLong(result, outBase, l);
                outBase += 8;
                */
            }
        }
        long time = System.currentTimeMillis() - mainStart;
        /*
        String str = new String(result, 0, len);
        System.out.println("("+str.length()+") '"+str+"'");
        */
        return time;
    }
    
    protected void _printResults(int rounds, String[] names, long[] times)
    {
        System.out.printf("  Averages after %d rounds:", rounds);
        double den = (double) rounds;
        for (int file = 0; file < names.length; ++file) {
            if (file > 0) {
                System.out.print(" / ");
            }
            System.out.printf(" %s(", names[file]);
            long time = times[file];
            double msecs = time / den;
            System.out.printf("%.1f)", msecs);
        }
        System.out.println();
    }

    public static void main(String[] args) throws Exception
    {
        new ManualUnsafePerf().test();
    }
}
