package org.apache.flink.runtime.scheduler;

import org.apache.flink.runtime.scheduler.strategy.ExecutionVertexID;

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
}
