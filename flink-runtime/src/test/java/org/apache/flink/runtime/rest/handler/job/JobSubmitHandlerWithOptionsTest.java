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
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.BlobServerOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.runtime.blob.BlobServer;
import org.apache.flink.runtime.blob.VoidBlobStore;
import org.apache.flink.runtime.dispatcher.DispatcherGateway;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobVertex;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.runtime.net.SSLUtilsTest;
import org.apache.flink.runtime.rest.handler.HandlerRequest;
import org.apache.flink.runtime.rest.messages.EmptyMessageParameters;
import org.apache.flink.runtime.rest.messages.job.JobSubmitRequestWithOptionsBody;
import org.apache.flink.runtime.rpc.RpcUtils;
import org.apache.flink.runtime.testingUtils.TestingUtils;
import org.apache.flink.runtime.webmonitor.TestingDispatcherGateway;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Tests for the {@link JobSubmitHandlerWithOptions}. */
@RunWith(Parameterized.class)
public class JobSubmitHandlerWithOptionsTest {
    @Parameterized.Parameters(name = "SSL enabled: {0}")
    public static Iterable<Tuple2<Boolean, String>> data() {
        ArrayList<Tuple2<Boolean, String>> parameters = new ArrayList<>(3);
        parameters.add(Tuple2.of(false, "no SSL"));
        for (String sslProvider : SSLUtilsTest.AVAILABLE_SSL_PROVIDERS) {
            parameters.add(Tuple2.of(true, sslProvider));
        }
        return parameters;
    }

    @ClassRule public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

    private final Configuration configuration;

    private BlobServer blobServer;

    public JobSubmitHandlerWithOptionsTest(Tuple2<Boolean, String> withSsl) {
        this.configuration =
                withSsl.f0
                        ? SSLUtilsTest.createInternalSslConfigWithKeyAndTrustStores(withSsl.f1)
                        : new Configuration();
    }

    @Before
    public void setup() throws IOException {
        Configuration config = new Configuration(configuration);
        config.setString(
                BlobServerOptions.STORAGE_DIRECTORY,
                TEMPORARY_FOLDER.newFolder().getAbsolutePath());

        blobServer = new BlobServer(config, new VoidBlobStore());
        blobServer.start();
    }

    @After
    public void teardown() throws IOException {
        if (blobServer != null) {
            blobServer.close();
        }
    }

    @Test
    public void testSuccessfulJobSubmissionWithOptionsSet() throws Exception {
        // Given
        final String testSavepointPath = "testSavepointPath";
        final boolean testAllowNonRestoredState = true;
        final boolean scaleToSingleTaskManager = true;
        final JobID initialTestJobID = JobID.generate();
        final JobID changedTestJobID = JobID.generate();
        final JobVertex jobVertex1 = new JobVertex("vertex1");
        final JobVertex jobVertex2 = new JobVertex("vertex2");
        jobVertex1.setParallelism(2);
        jobVertex2.setParallelism(6);
        final Map<String, Integer> testVertexIdParallelismMap =
                Stream.of(
                                new String[][] {
                                    {jobVertex1.getID().toString(), "3"},
                                    {jobVertex2.getID().toString(), "7"},
                                })
                        .collect(
                                Collectors.toMap(
                                        data -> data[0], data -> Integer.valueOf(data[1])));
        final int numberOfTaskSlots = 5;
        final String validClassPathUrl = "file://jar1";
        final String invalidClassPathUrl = "invalidUrl";
        final List<String> classPathUrls =
                Stream.of(validClassPathUrl, invalidClassPathUrl).collect(Collectors.toList());
        final Path jobGraphFile = TEMPORARY_FOLDER.newFile().toPath();
        configuration.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, numberOfTaskSlots);

        try (ObjectOutputStream objectOut =
                new ObjectOutputStream(Files.newOutputStream(jobGraphFile))) {
            JobGraph jobGraph = new JobGraph(initialTestJobID, "testJob");
            jobGraph.addVertex(jobVertex1);
            jobGraph.addVertex(jobVertex2);
            objectOut.writeObject(jobGraph);
        }

        CompletableFuture<JobGraph> submittedJobGraphFuture = new CompletableFuture<>();
        DispatcherGateway dispatcherGateway =
                new TestingDispatcherGateway.Builder()
                        .setBlobServerPort(blobServer.getPort())
                        .setSubmitFunction(
                                submittedJobGraph -> {
                                    submittedJobGraphFuture.complete(submittedJobGraph);
                                    return CompletableFuture.completedFuture(Acknowledge.get());
                                })
                        .build();

        JobSubmitHandlerWithOptions handler =
                new JobSubmitHandlerWithOptions(
                        () -> CompletableFuture.completedFuture(dispatcherGateway),
                        RpcUtils.INF_TIMEOUT,
                        Collections.emptyMap(),
                        TestingUtils.defaultExecutor(),
                        configuration);

        JobSubmitRequestWithOptionsBody request =
                new JobSubmitRequestWithOptionsBody(
                        jobGraphFile.getFileName().toString(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        changedTestJobID.toHexString(),
                        testSavepointPath,
                        testAllowNonRestoredState,
                        testVertexIdParallelismMap,
                        scaleToSingleTaskManager,
                        classPathUrls);

        // When
        handler.handleRequest(
                        new HandlerRequest<>(
                                request,
                                EmptyMessageParameters.getInstance(),
                                Collections.emptyMap(),
                                Collections.emptyMap(),
                                Collections.singletonList(jobGraphFile.toFile())),
                        dispatcherGateway)
                .get();

        // Then
        Assert.assertTrue("No JobGraph was submitted.", submittedJobGraphFuture.isDone());
        final JobGraph submittedJobGraph = submittedJobGraphFuture.get();
        Assert.assertEquals(
                testAllowNonRestoredState,
                submittedJobGraph.getSavepointRestoreSettings().allowNonRestoredState());
        Assert.assertEquals(
                testSavepointPath,
                submittedJobGraph.getSavepointRestoreSettings().getRestorePath());
        Assert.assertEquals(
                changedTestJobID.toHexString(), submittedJobGraph.getJobID().toHexString());
        Assert.assertEquals(
                (int) testVertexIdParallelismMap.get(jobVertex1.getID().toString()),
                submittedJobGraph.findVertexByID(jobVertex1.getID()).getParallelism());
        Assert.assertEquals(
                numberOfTaskSlots,
                submittedJobGraph.findVertexByID(jobVertex2.getID()).getParallelism());
        Assert.assertEquals(
                Stream.of(new URL(validClassPathUrl)).collect(Collectors.toList()),
                submittedJobGraph.getClasspaths());
    }

    @Test
    public void testSuccessfulJobSubmissionWithOptionsPartlySet() throws Exception {
        // Given
        final boolean testAllowNonRestoredState = false;
        final boolean scaleToSingleTaskManager = false;
        final JobID initialTestJobID = JobID.generate();
        final JobID changedTestJobID = JobID.generate();
        final JobVertex jobVertex1 = new JobVertex("vertex1");
        final JobVertex jobVertex2 = new JobVertex("vertex2");
        jobVertex1.setParallelism(2);
        jobVertex2.setParallelism(6);
        final int numberOfTaskSlots = 5;
        final Path jobGraphFile = TEMPORARY_FOLDER.newFile().toPath();
        configuration.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, numberOfTaskSlots);

        try (ObjectOutputStream objectOut =
                new ObjectOutputStream(Files.newOutputStream(jobGraphFile))) {
            JobGraph jobGraph = new JobGraph(initialTestJobID, "testJob");
            jobGraph.addVertex(jobVertex1);
            jobGraph.addVertex(jobVertex2);
            objectOut.writeObject(jobGraph);
        }

        CompletableFuture<JobGraph> submittedJobGraphFuture = new CompletableFuture<>();
        DispatcherGateway dispatcherGateway =
                new TestingDispatcherGateway.Builder()
                        .setBlobServerPort(blobServer.getPort())
                        .setSubmitFunction(
                                submittedJobGraph -> {
                                    submittedJobGraphFuture.complete(submittedJobGraph);
                                    return CompletableFuture.completedFuture(Acknowledge.get());
                                })
                        .build();

        JobSubmitHandlerWithOptions handler =
                new JobSubmitHandlerWithOptions(
                        () -> CompletableFuture.completedFuture(dispatcherGateway),
                        RpcUtils.INF_TIMEOUT,
                        Collections.emptyMap(),
                        TestingUtils.defaultExecutor(),
                        configuration);

        JobSubmitRequestWithOptionsBody request =
                new JobSubmitRequestWithOptionsBody(
                        jobGraphFile.getFileName().toString(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        changedTestJobID.toHexString(),
                        null,
                        testAllowNonRestoredState,
                        null,
                        scaleToSingleTaskManager,
                        null);

        // When
        handler.handleRequest(
                        new HandlerRequest<>(
                                request,
                                EmptyMessageParameters.getInstance(),
                                Collections.emptyMap(),
                                Collections.emptyMap(),
                                Collections.singletonList(jobGraphFile.toFile())),
                        dispatcherGateway)
                .get();

        // Then
        Assert.assertTrue("No JobGraph was submitted.", submittedJobGraphFuture.isDone());
        final JobGraph submittedJobGraph = submittedJobGraphFuture.get();
        Assert.assertEquals(
                testAllowNonRestoredState,
                submittedJobGraph.getSavepointRestoreSettings().allowNonRestoredState());
        Assert.assertNull(submittedJobGraph.getSavepointRestoreSettings().getRestorePath());
        Assert.assertEquals(
                changedTestJobID.toHexString(), submittedJobGraph.getJobID().toHexString());
        Assert.assertEquals(
                jobVertex1.getParallelism(),
                submittedJobGraph.findVertexByID(jobVertex1.getID()).getParallelism());
        Assert.assertEquals(
                jobVertex2.getParallelism(),
                submittedJobGraph.findVertexByID(jobVertex2.getID()).getParallelism());
        Assert.assertEquals(Collections.emptyList(), submittedJobGraph.getClasspaths());
    }
}
