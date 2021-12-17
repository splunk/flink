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

package org.apache.flink.runtime.rest.messages.job;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/** Request for submitting a job with options. */
public class JobSubmitRequestWithOptionsBody extends JobSubmitRequestBody {

    private static final String FIELD_NAME_JOB_ID = "jobId";
    private static final String FIELD_NAME_SAVEPOINT = "savepointDirectoryPath";
    private static final String FIELD_NAME_ALLOW_NON_RESTORED_STATE = "allowNonRestoredState";
    private static final String FIELD_NAME_OPERATOR_PARALLELISM_CHANGE_MAP =
            "operatorParallelismChangeMap";
    private static final String FIELD_NAME_SCALE_TO_SINGLE_TASK_MANAGER =
            "scaleToSingleTaskManager";

    @JsonProperty(FIELD_NAME_JOB_ID)
    @Nonnull
    public final String jobId;

    @JsonProperty(FIELD_NAME_SAVEPOINT)
    @Nullable
    public final String savepointDirectoryPath;

    @JsonProperty(FIELD_NAME_ALLOW_NON_RESTORED_STATE)
    public final boolean allowNonRestoredState;

    @JsonProperty(FIELD_NAME_OPERATOR_PARALLELISM_CHANGE_MAP)
    @Nullable
    public final Map<String, Integer> operatorParallelismChangeMap;

    @JsonProperty(FIELD_NAME_SCALE_TO_SINGLE_TASK_MANAGER)
    public final boolean scaleToSingleTaskManager;

    @JsonCreator
    public JobSubmitRequestWithOptionsBody(
            @Nullable @JsonProperty(FIELD_NAME_JOB_GRAPH) String jobGraphFileName,
            @Nullable @JsonProperty(FIELD_NAME_JOB_JARS) Collection<String> jarFileNames,
            @Nullable @JsonProperty(FIELD_NAME_JOB_ARTIFACTS)
                    Collection<DistributedCacheFile> artifactFileNames,
            @Nonnull @JsonProperty(value = FIELD_NAME_JOB_ID, required = true) String jobId,
            @Nullable @JsonProperty(FIELD_NAME_SAVEPOINT) String savepointDirectoryPath,
            @JsonProperty(FIELD_NAME_ALLOW_NON_RESTORED_STATE) boolean allowNonRestoredState,
            @Nullable @JsonProperty(FIELD_NAME_OPERATOR_PARALLELISM_CHANGE_MAP)
                    Map<String, Integer> operatorParallelismChangeMap,
            @JsonProperty(FIELD_NAME_SCALE_TO_SINGLE_TASK_MANAGER)
                    boolean scaleToSingleTaskManager) {
        super(jobGraphFileName, jarFileNames, artifactFileNames);
        this.jobId = jobId;
        this.savepointDirectoryPath = savepointDirectoryPath;
        this.allowNonRestoredState = allowNonRestoredState;
        this.operatorParallelismChangeMap = operatorParallelismChangeMap;
        this.scaleToSingleTaskManager = scaleToSingleTaskManager;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JobSubmitRequestWithOptionsBody that = (JobSubmitRequestWithOptionsBody) o;
        return Objects.equals(jobGraphFileName, that.jobGraphFileName)
                && Objects.equals(jarFileNames, that.jarFileNames)
                && Objects.equals(artifactFileNames, that.artifactFileNames)
                && Objects.equals(jobId, that.jobId)
                && Objects.equals(savepointDirectoryPath, that.savepointDirectoryPath)
                && allowNonRestoredState == that.allowNonRestoredState
                && Objects.equals(operatorParallelismChangeMap, that.operatorParallelismChangeMap)
                && scaleToSingleTaskManager == that.scaleToSingleTaskManager;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                jobGraphFileName,
                jarFileNames,
                artifactFileNames,
                jobId,
                savepointDirectoryPath,
                allowNonRestoredState,
                operatorParallelismChangeMap,
                scaleToSingleTaskManager);
    }

    @Override
    public String toString() {
        return "JobSubmitRequestWithOptionsBody{"
                + "jobGraphFileName='"
                + jobGraphFileName
                + '\''
                + ", jarFileNames="
                + jarFileNames
                + ", artifactFileNames="
                + artifactFileNames
                + ", jobId='"
                + jobId
                + '\''
                + ", savepointDirectoryPath'="
                + savepointDirectoryPath
                + '\''
                + ", allowNonRestoredState="
                + allowNonRestoredState
                + ", operatorParallelismChangeMap="
                + operatorParallelismChangeMap
                + ", scaleToSingleTaskManager="
                + scaleToSingleTaskManager
                + '}';
    }
}
