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


import java.util.concurrent.Callable;

import com.ning.compress.lzf.ChunkEncoder;
import com.ning.compress.lzf.LZFChunk;
import com.ning.compress.lzf.util.ChunkEncoderFactory;

/**
 * @author C&eacute;drik LIME
 */
class CompressTask implements Callable<LZFChunk> {
	private static final ThreadLocal<ChunkEncoder> ENCODER = new ThreadLocal<ChunkEncoder>() {
		@Override
		protected ChunkEncoder initialValue() {
			return ChunkEncoderFactory.optimalInstance();
		}
	};

	protected byte[] data;
	protected int offset, length;
	protected BlockManager blockManager;

	public CompressTask(byte[] input, int offset, int length, BlockManager blockManager) {
		super();
		this.data = input;
		this.offset = offset;
		this.length = length;
		this.blockManager = blockManager;
	}
	public CompressTask(byte[] input, BlockManager blockManager) {
		this(input, 0, input.length, blockManager);
	}

	/** {@inheritDoc} */
	@Override
	public LZFChunk call() {
		if (data != null) {
			LZFChunk lzfChunk = ENCODER.get().encodeChunk(data, offset, length);
			// input data is fully processed, we can now discard it
			blockManager.releaseBlockToPool(data);
			return lzfChunk;
		} else {
			// cleanup time!
			ENCODER.remove();
			return null;
		}
	}

}
