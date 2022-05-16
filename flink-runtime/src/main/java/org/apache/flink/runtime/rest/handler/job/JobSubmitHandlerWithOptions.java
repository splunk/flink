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

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.core.fs.Path;
import org.apache.flink.runtime.dispatcher.DispatcherGateway;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobVertex;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings;
import org.apache.flink.runtime.rest.messages.job.JobSubmitRequestWithOptionsBody;
import org.apache.flink.runtime.rest.messages.job.JobSubmitWithOptionsHeaders;
import org.apache.flink.runtime.webmonitor.retriever.GatewayRetriever;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** This handler can be used to submit jobs to a Flink cluster with savepoint settings. */
public class JobSubmitHandlerWithOptions
        extends AbstractJobSubmitHandler<JobSubmitRequestWithOptionsBody> {

    public JobSubmitHandlerWithOptions(
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
                JobSubmitWithOptionsHeaders.getInstance(),
                configuration);
    }

    @Override
    protected CompletableFuture<JobGraph> applyOptionsToJobGraph(
            CompletableFuture<JobGraph> jobGraphFuture,
            JobSubmitRequestWithOptionsBody requestBody,
            Map<String, Path> nameToFile) {
        return jobGraphFuture.thenApply(
                jobGraph -> {
                    setJobId(jobGraph, requestBody.jobId);

                    if (requestBody.savepointDirectoryPath != null) {
                        setSavepointRestoreSettings(
                                jobGraph,
                                requestBody.savepointDirectoryPath,
                                requestBody.allowNonRestoredState);
                    }

                    if (requestBody.operatorParallelismChangeMap != null) {
                        setOperatorParallelisms(jobGraph, requestBody.operatorParallelismChangeMap);
                    }

                    if (requestBody.scaleToSingleTaskManager) {
                        scaleOperatorParallelismsToAvailableTaskSlots(jobGraph);
                    }

                    if (requestBody.classpathUrls != null) {
                        jobGraph.setClasspaths(
                                requestBody.classpathUrls.stream()
                                        .flatMap(
                                                a -> {
                                                    try {
                                                        return Stream.of(new URL(a));
                                                    } catch (MalformedURLException e) {
                                                        return Stream.empty();
                                                    }
                                                })
                                        .collect(Collectors.toList()));
                    }

                    return jobGraph;
                });
    }

    private void setSavepointRestoreSettings(
            JobGraph jobGraph, String savepointFileName, boolean allowNonRestoredState) {
        SavepointRestoreSettings savepointRestoreSettings =
                SavepointRestoreSettings.forPath(savepointFileName, allowNonRestoredState);
        jobGraph.setSavepointRestoreSettings(savepointRestoreSettings);
    }

    private void setOperatorParallelisms(
            JobGraph jobGraph, Map<String, Integer> operatorParallelismChangeMap) {
        for (Map.Entry<String, Integer> override : operatorParallelismChangeMap.entrySet()) {
            String vertexId = override.getKey();
            Integer parallelism = override.getValue();
            JobVertex jobVertex = jobGraph.findVertexByID(JobVertexID.fromHexString(vertexId));
            if (jobVertex != null) {
                jobVertex.setParallelism(parallelism);
            } else {
                log.warn(
                        "JobGraph changed. Task {} not present anymore. Skipping parallelism override {}",
                        vertexId,
                        parallelism);
            }
        }
    }

    private void scaleOperatorParallelismsToAvailableTaskSlots(JobGraph jobGraph) {
        jobGraph.getVertices()
                .forEach(
                        v ->
                                v.setParallelism(
                                        Math.min(
                                                v.getParallelism(),
                                                configuration.getInteger(
                                                        TaskManagerOptions.NUM_TASK_SLOTS))));
    }

    private static void setJobId(JobGraph jobGraph, String jobID) {
        jobGraph.setJobID(JobID.fromHexString(jobID));
    }
}
