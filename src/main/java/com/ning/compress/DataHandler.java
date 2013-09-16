package com.ning.compress;
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


import java.io.IOException;

/**
 * Interface used by {@link Uncompressor} implementations: receives
 * uncompressed data and processes it appropriately.
 *
 * @since 0.9.4
 */
public interface DataHandler
{
    /**
     * Method called with uncompressed data as it becomes available.
     *<p>
     * NOTE: return value was added (from void to boolean) in 0.9.9
     * 
     * @return True, if caller should process and feed more data; false if
     *   caller is not interested in more data and processing should be terminated
     *   (and {@link #allDataHandled} should be called immediately)
     */
    public boolean handleData(byte[] buffer, int offset, int len) throws IOException;

    /**
     * Method called after last call to {@link #handleData}, for successful
     * operation, if and when caller is informed about end of content
     * Note that if an exception thrown by {@link #handleData} has caused processing
     * to be aborted, this method might not get called.
     * Implementation may choose to free resources, flush state, or perform
     * validation at this point.
     */
    public void allDataHandled() throws IOException;
}
