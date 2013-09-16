package com.ning.compress.lzf.parallel;
/*
 *
 * Copyright 2009-2013 Ning, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
*/


import java.io.OutputStream;
import java.util.concurrent.Future;

import com.ning.compress.lzf.LZFChunk;

/**
 * @author C&eacute;drik LIME
 */
class WriteTask implements Runnable {
    private final OutputStream output;
    private final Future<LZFChunk> lzfFuture;
    private final PLZFOutputStream caller;

    public WriteTask(OutputStream output, Future<LZFChunk> lzfFuture, PLZFOutputStream caller) {
        super();
        this.output = output;
        this.lzfFuture = lzfFuture;
        this.caller = caller;
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
            caller.writeException = e;
        }
    }
}
