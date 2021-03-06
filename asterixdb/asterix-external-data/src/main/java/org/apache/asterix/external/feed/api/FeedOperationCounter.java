/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.asterix.external.feed.api;

import org.apache.asterix.active.ActiveJob;

public class FeedOperationCounter {
    private ActiveJob feedJobInfo;
    private int partitionCount;
    private boolean failedIngestion = false;

    public FeedOperationCounter(int partitionCount) {
        this.partitionCount = partitionCount;
    }

    public int getPartitionCount() {
        return partitionCount;
    }

    public void setPartitionCount(int partitionCount) {
        this.partitionCount = partitionCount;
    }

    public boolean isFailedIngestion() {
        return failedIngestion;
    }

    public void setFailedIngestion(boolean failedIngestion) {
        this.failedIngestion = failedIngestion;
    }

    public ActiveJob getFeedJobInfo() {
        return feedJobInfo;
    }

    public void setFeedJobInfo(ActiveJob feedJobInfo) {
        this.feedJobInfo = feedJobInfo;
    }

    public int decrementAndGet() {
        return --partitionCount;
    }
}
