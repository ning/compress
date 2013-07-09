package com.ning.compress.lzf.parallel;

import java.io.OutputStream;
import java.util.concurrent.Future;

import com.ning.compress.lzf.LZFChunk;

/**
 * @author C&eacute;drik LIME
 */
class WriteTask implements Runnable {
    private final OutputStream output;
    private final Future<LZFChunk> lzfFuture;

    public WriteTask(OutputStream output, Future<LZFChunk> lzfFuture) {
        super();
        this.output = output;
        this.lzfFuture = lzfFuture;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        try {
            LZFChunk lzfChunk = lzfFuture.get();
            while (lzfChunk != null) {
                output.write(lzfChunk.getData());
                lzfChunk = lzfChunk.next();
            }
        } catch (Exception e) {
            throwRTE(e);
        }
    }

    private RuntimeException throwRTE(Exception e) {
        throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
    }
}
