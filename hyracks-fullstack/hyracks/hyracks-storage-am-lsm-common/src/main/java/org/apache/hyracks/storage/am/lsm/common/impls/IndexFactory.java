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

package org.apache.hyracks.storage.am.lsm.common.impls;

import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.api.io.FileReference;
import org.apache.hyracks.api.io.IIOManager;
import org.apache.hyracks.storage.am.common.api.IIndex;
import org.apache.hyracks.storage.am.common.api.IMetadataManagerFactory;
import org.apache.hyracks.storage.am.common.api.IndexException;
import org.apache.hyracks.storage.common.buffercache.IBufferCache;
import org.apache.hyracks.storage.common.file.IFileMapProvider;

public abstract class IndexFactory<T extends IIndex> {

    protected final IIOManager ioManager;
    protected final IBufferCache bufferCache;
    protected final IFileMapProvider fileMapProvider;
    protected final IMetadataManagerFactory freePageManagerFactory;

    public IndexFactory(IIOManager ioManager, IBufferCache bufferCache, IFileMapProvider fileMapProvider,
            IMetadataManagerFactory freePageManagerFactory) {
        this.ioManager = ioManager;
        this.bufferCache = bufferCache;
        this.fileMapProvider = fileMapProvider;
        this.freePageManagerFactory = freePageManagerFactory;
    }

    public abstract T createIndexInstance(FileReference file) throws IndexException, HyracksDataException;

    public IBufferCache getBufferCache() {
        return bufferCache;
    }
}
