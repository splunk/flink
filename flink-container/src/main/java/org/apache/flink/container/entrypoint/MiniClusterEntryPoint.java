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

package org.apache.flink.container.entrypoint;

import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.GlobalConfiguration;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.runtime.entrypoint.EntrypointClusterConfiguration;
import org.apache.flink.runtime.entrypoint.EntrypointClusterConfigurationParserFactory;
import org.apache.flink.runtime.entrypoint.parser.CommandLineParser;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.runtime.minicluster.MiniClusterConfiguration;
import org.apache.flink.runtime.security.SecurityConfiguration;
import org.apache.flink.runtime.security.SecurityUtils;
import org.apache.flink.runtime.util.EnvironmentInformation;
import org.apache.flink.runtime.util.JvmShutdownSafeguard;
import org.apache.flink.runtime.util.SignalHandler;
import org.apache.flink.util.ExceptionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.UndeclaredThrowableException;

/**
 * Entry point for a mini cluster.
 */
public class MiniClusterEntryPoint {
    protected static final Logger LOG = LoggerFactory.getLogger(MiniClusterEntryPoint.class);

    protected static final int SUCCESS_RETURN_CODE = 0;
    protected static final int STARTUP_FAILURE_RETURN_CODE = 1;
    protected static final int RUNTIME_FAILURE_RETURN_CODE = 2;

    public static void main(String[] args) {
        // startup checks and logging
        EnvironmentInformation.logEnvironmentInfo(LOG, MiniClusterEntryPoint.class.getSimpleName(), args);
        SignalHandler.register(LOG);
        JvmShutdownSafeguard.installAsShutdownHook(LOG);

        long maxOpenFileHandles = EnvironmentInformation.getOpenFileHandlesLimit();

        if (maxOpenFileHandles != -1L) {
            LOG.info("Maximum number of open file descriptors is {}.", maxOpenFileHandles);
        } else {
            LOG.info("Cannot determine the maximum number of open file descriptors");
        }

        EntrypointClusterConfiguration entrypointClusterConfiguration = null;
        final CommandLineParser<EntrypointClusterConfiguration> commandLineParser = new CommandLineParser<>(new EntrypointClusterConfigurationParserFactory());

        try {
            entrypointClusterConfiguration = commandLineParser.parse(args);
        } catch (Exception e) {
            LOG.error("Could not parse command line arguments {}.", args, e);
            commandLineParser.printHelp(MiniClusterEntryPoint.class.getSimpleName());
            System.exit(STARTUP_FAILURE_RETURN_CODE);
        }

        Configuration configuration = GlobalConfiguration.loadConfiguration(entrypointClusterConfiguration.getConfigDir());

        final MiniClusterConfiguration miniClusterConfiguration = new MiniClusterConfiguration.Builder()
                .setConfiguration(configuration)
                .setNumTaskManagers(configuration.getInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, 1))
                .setNumSlotsPerTaskManager(configuration.getInteger(TaskManagerOptions.NUM_TASK_SLOTS, 1))
                .build();

        MiniCluster miniCluster = new MiniCluster(miniClusterConfiguration);

        try {
            SecurityUtils.install(new SecurityConfiguration(configuration));
        } catch (Exception e) {
            LOG.error("Failed to install security configuration.", e);
            System.exit(STARTUP_FAILURE_RETURN_CODE);
        }

        try {
            SecurityUtils.getInstalledContext().runSecured(() -> {
                miniCluster.start();
                return null;
            });
        } catch (Throwable t) {
            final Throwable strippedThrowable = ExceptionUtils.stripException(t, UndeclaredThrowableException.class);
            LOG.error("MiniCluster initialization failed.", strippedThrowable);
            System.exit(STARTUP_FAILURE_RETURN_CODE);
        }

        miniCluster.getTerminationFuture().whenComplete((unused, throwable) -> {
            final int returnCode;

            if (throwable != null) {
                returnCode = RUNTIME_FAILURE_RETURN_CODE;
            } else {
                returnCode = SUCCESS_RETURN_CODE;
            }

            LOG.info("Terminating cluster entrypoint process {} with exit code {}.", MiniClusterEntryPoint.class.getSimpleName(), returnCode, throwable);
            System.exit(returnCode);
        });
    }
}
