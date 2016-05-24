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

package org.apache.asterix.runtime.aggregates.std;

import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluatorFactory;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.data.std.api.IPointable;
import org.apache.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public class SqlAvgAggregateFunction extends AbstractAvgAggregateFunction {

    public SqlAvgAggregateFunction(IScalarEvaluatorFactory[] args, IHyracksTaskContext context)
            throws AlgebricksException {
        super(args, context);
    }

    @Override
    public void step(IFrameTupleReference tuple) throws AlgebricksException {
        processDataValues(tuple);
    }

    @Override
    public void finish(IPointable result) throws AlgebricksException {
        finishFinalResults(result);
    }

    @Override
    public void finishPartial(IPointable result) throws AlgebricksException {
        finishPartialResults(result);
    }

    @Override
    protected void processNull() {
    }
}
