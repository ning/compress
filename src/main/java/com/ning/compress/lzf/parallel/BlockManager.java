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


import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * @author C&eacute;drik LIME
 */
class BlockManager {
    /* used as a blocking Stack (FIFO) */
    private final BlockingDeque<byte[]> blockPool;

    public BlockManager(int blockPoolSize, int blockSize) {
//        log.debug("Using block pool size of " + blockPoolSize);
        blockPool = new LinkedBlockingDeque<byte[]>(blockPoolSize);
        for (int i = 0; i < blockPoolSize; ++i) {
            blockPool.addFirst(new byte[blockSize]);
        }
    }

    public byte[] getBlockFromPool() {
        byte[] block = null;
        try {
            block = blockPool.takeFirst();
        } catch (InterruptedException e) {
        	throw new RuntimeException(e);
        }
        return block;
    }

    public void releaseBlockToPool(byte[] block) {
        assert ! blockPool.contains(block);
//        Arrays.fill(block, (byte)0);
        try {
            blockPool.putLast(block);
        } catch (InterruptedException e) {
        	throw new RuntimeException(e);
        }
    }

}
