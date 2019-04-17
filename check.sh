#!/usr/bin/env bash
################################################################################
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
# limitations under the License.
################################################################################

REMOTE=${REMOTE:-apache}
BRANCH=${BRANCH:-master}

source ./tools/travis/setup_maven.sh

git clone --single-branch -b ${BRANCH} https://github.com/${REMOTE}/flink

cd flink

LOG4J_PROPERTIES=${FLINK_DIR}/tools/log4j-travis.properties

MVN_LOGGING_OPTIONS="-Dlog4j.configuration=file://$LOG4J_PROPERTIES -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
MVN_COMPILE_OPTIONS="-nsu -B"

MVN_COMPILE="mvn ${MVN_COMPILE_OPTIONS} ${MVN_LOGGING_OPTIONS} ${PROFILE} org.owasp:dependency-check-maven:aggregate"

eval "${MVN_COMPILE}"
exit $?
