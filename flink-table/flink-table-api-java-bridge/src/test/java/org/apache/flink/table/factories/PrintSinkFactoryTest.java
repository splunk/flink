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

package org.apache.flink.table.factories;

import org.apache.flink.connector.print.table.PrintConnectorOptions;
import org.apache.flink.connector.print.table.PrintTableSinkFactory;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.Column;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.connector.sink.DynamicTableSink;
import org.apache.flink.table.connector.sink.abilities.SupportsPartitioning;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.flink.table.factories.utils.FactoryMocks.createTableSink;
import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link PrintTableSinkFactory}. */
public class PrintSinkFactoryTest {

    private static final ResolvedSchema SCHEMA =
            ResolvedSchema.of(
                    Column.physical("f0", DataTypes.STRING()),
                    Column.physical("f1", DataTypes.BIGINT()),
                    Column.physical("f2", DataTypes.BIGINT()));

    @Test
    public void testPrint() {
        Map<String, String> properties = new HashMap<>();
        properties.put("connector", "print");
        properties.put(PrintConnectorOptions.PRINT_IDENTIFIER.key(), "my_print");
        properties.put(PrintConnectorOptions.STANDARD_ERROR.key(), "true");

        List<String> partitionKeys = Arrays.asList("f0", "f1");
        DynamicTableSink sink = createTableSink(SCHEMA, partitionKeys, properties);

        assertThat(sink.asSummaryString()).isEqualTo("Print to System.err");
        assertThat(sink).isInstanceOf(SupportsPartitioning.class);
    }
}
