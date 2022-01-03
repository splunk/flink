/*
 * Copyright (c) 2021 Splunk, Inc. All rights reserved.
 */

package org.apache.flink.container.entrypoint;

import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.GlobalConfiguration;
import org.apache.flink.runtime.entrypoint.EntrypointClusterConfiguration;
import org.apache.flink.runtime.entrypoint.EntrypointClusterConfigurationParserFactory;
import org.apache.flink.runtime.entrypoint.StandaloneSessionClusterEntrypoint;
import org.apache.flink.runtime.entrypoint.parser.CommandLineParser;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.runtime.minicluster.MiniClusterConfiguration;
import org.apache.flink.runtime.util.EnvironmentInformation;
import org.apache.flink.runtime.util.JvmShutdownSafeguard;
import org.apache.flink.runtime.util.SignalHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point for a mini cluster. */
public class MiniClusterEntryPoint {
    protected static final Logger LOG = LoggerFactory.getLogger(MiniClusterEntryPoint.class);

    public static void main(String[] args) {
        // startup checks and logging
        EnvironmentInformation.logEnvironmentInfo(
                LOG, StandaloneSessionClusterEntrypoint.class.getSimpleName(), args);
        SignalHandler.register(LOG);
        JvmShutdownSafeguard.installAsShutdownHook(LOG);

        EntrypointClusterConfiguration entrypointClusterConfiguration = null;
        final CommandLineParser<EntrypointClusterConfiguration> commandLineParser =
                new CommandLineParser<>(new EntrypointClusterConfigurationParserFactory());

        try {
            entrypointClusterConfiguration = commandLineParser.parse(args);
        } catch (Exception e) {
            LOG.error("Could not parse command line arguments {}.", args, e);
            commandLineParser.printHelp(MiniClusterEntryPoint.class.getSimpleName());
            System.exit(1);
        }

        Configuration configuration =
                GlobalConfiguration.loadConfiguration(
                        entrypointClusterConfiguration.getConfigDir());

        final MiniClusterConfiguration miniClusterConfiguration =
                new MiniClusterConfiguration.Builder()
                        .setConfiguration(configuration)
                        // MiniClusterConfiguration.Builder defaults to 1 TM unless configured
                        // manually
                        .setNumTaskManagers(
                                configuration.getInteger(
                                        ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, 1))
                        .build();

        MiniCluster miniCluster = new MiniCluster(miniClusterConfiguration);

        try {
            miniCluster.start();
        } catch (Exception e) {
            LOG.error(
                    "Failed to start MiniCluster with configuration {}.",
                    miniClusterConfiguration,
                    e);
            System.exit(1);
        }
    }
}
