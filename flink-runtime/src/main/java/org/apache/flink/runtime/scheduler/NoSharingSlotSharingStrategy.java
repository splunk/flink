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

package org.apache.flink.runtime.scheduler;

import org.apache.flink.runtime.jobmanager.scheduler.CoLocationGroup;
import org.apache.flink.runtime.jobmanager.scheduler.SlotSharingGroup;
import org.apache.flink.runtime.scheduler.strategy.ExecutionVertexID;
import org.apache.flink.runtime.scheduler.strategy.SchedulingTopology;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** This strategy assigns each execution vertex a separate execution slot sharing group. */
public class NoSharingSlotSharingStrategy implements SlotSharingStrategy {

    private final Map<ExecutionVertexID, ExecutionSlotSharingGroup> executionSlotSharingGroupMap;

    public NoSharingSlotSharingStrategy() {
        this.executionSlotSharingGroupMap = new LinkedHashMap<>();
    }

    @Override
    public ExecutionSlotSharingGroup getExecutionSlotSharingGroup(
            ExecutionVertexID executionVertexId) {
        return executionSlotSharingGroupMap.computeIfAbsent(
                executionVertexId,
                x -> {
                    ExecutionSlotSharingGroup newGroup = new ExecutionSlotSharingGroup();
                    newGroup.addVertex(x);
                    return newGroup;
                });
    }

    @Override
    public Set<ExecutionSlotSharingGroup> getExecutionSlotSharingGroups() {
        return new HashSet<>(executionSlotSharingGroupMap.values());
    }

    public static SlotSharingStrategy createInstance(
            SchedulingTopology topology,
            Set<SlotSharingGroup> logicalSlotSharingGroups,
            Set<CoLocationGroup> coLocationGroups) {
        return new NoSharingSlotSharingStrategy();
    }
}
