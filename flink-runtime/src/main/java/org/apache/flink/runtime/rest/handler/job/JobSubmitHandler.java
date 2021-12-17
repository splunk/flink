/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.rest.handler.job;

import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.Path;
import org.apache.flink.runtime.dispatcher.DispatcherGateway;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.rest.messages.job.JobSubmitHeaders;
import org.apache.flink.runtime.rest.messages.job.JobSubmitRequestBody;
import org.apache.flink.runtime.webmonitor.retriever.GatewayRetriever;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** This handler can be used to submit jobs to a Flink cluster. */
public final class JobSubmitHandler extends AbstractJobSubmitHandler<JobSubmitRequestBody> {

    public JobSubmitHandler(
            GatewayRetriever<? extends DispatcherGateway> leaderRetriever,
            Time timeout,
            Map<String, String> headers,
            Executor executor,
            Configuration configuration) {
        super(
                leaderRetriever,
                timeout,
                headers,
                executor,
                JobSubmitHeaders.getInstance(),
                configuration);
    }

    // No additional options are applied for default JobSubmitHandler
    @Override
    protected CompletableFuture<JobGraph> applyOptionsToJobGraph(
            CompletableFuture<JobGraph> jobGraphFuture,
            JobSubmitRequestBody requestBody,
            Map<String, Path> nameToFile) {
        return jobGraphFuture;
    }
}
