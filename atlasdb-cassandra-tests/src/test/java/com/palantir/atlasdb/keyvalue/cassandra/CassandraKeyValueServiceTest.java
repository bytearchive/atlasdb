/**
 * Copyright 2016 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.atlasdb.keyvalue.cassandra;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.palantir.atlasdb.AtlasDbConstants;
import com.palantir.atlasdb.cassandra.CassandraKeyValueServiceConfigManager;
import com.palantir.atlasdb.encoding.PtBytes;
import com.palantir.atlasdb.keyvalue.api.Cell;
import com.palantir.atlasdb.keyvalue.api.KeyValueService;
import com.palantir.atlasdb.keyvalue.api.TableReference;
import com.palantir.atlasdb.keyvalue.api.Value;

public class CassandraKeyValueServiceTest {

    private KeyValueService keyValueService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(100);
    private final Cell TABLE_CELL = Cell.create(PtBytes.toBytes("row0"), PtBytes.toBytes("col0"));
    private final byte[] TABLE_VALUE = PtBytes.toBytes("xyz");

    @Before
    public void setupKVS() {
//        keyValueService = CassandraKeyValueService.create(
//                CassandraKeyValueServiceConfigManager.createSimpleManager(CassandraTestSuite.CASSANDRA_KVS_CONFIG));
    }

    @Ignore
    @Test
    public void testCreateTableCaseInsensitive() {
        TableReference table1 = TableReference.createFromFullyQualifiedName("ns.tAbLe");
        TableReference table2 = TableReference.createFromFullyQualifiedName("ns.table");
        TableReference table3 = TableReference.createFromFullyQualifiedName("ns.TABle");
        keyValueService.createTable(table1, AtlasDbConstants.GENERIC_TABLE_METADATA);
        keyValueService.createTable(table2, AtlasDbConstants.GENERIC_TABLE_METADATA);
        keyValueService.createTable(table3, AtlasDbConstants.GENERIC_TABLE_METADATA);
        Set<TableReference> allTables = keyValueService.getAllTableNames();
        Preconditions.checkArgument(allTables.contains(table1));
        Preconditions.checkArgument(!allTables.contains(table2));
        Preconditions.checkArgument(!allTables.contains(table3));
    }

    @Test
    public void testCreateTablesWithMultipleKVS() throws Exception {

        TableReference table1 = TableReference.createFromFullyQualifiedName("ns.table1");

        CyclicBarrier barrier = new CyclicBarrier(32);


        try {
            for(int i=0; i < 200; i++) {
                async(() -> {
                    CassandraKeyValueService keyValueService = CassandraKeyValueService.create(
                            CassandraKeyValueServiceConfigManager.createSimpleManager(CassandraTestSuite.CASSANDRA_KVS_CONFIG));
                   // keyValueService.supportsCAS = false;
                    barrier.await();
                    keyValueService.createTable(table1, AtlasDbConstants.GENERIC_TABLE_METADATA);
                    return null;
                });
            }
        } catch (Exception e) {
            throw e;
        }

        KeyValueService keyValueService = CassandraKeyValueService.create(
                CassandraKeyValueServiceConfigManager.createSimpleManager(CassandraTestSuite.CASSANDRA_KVS_CONFIG));

        executorService.shutdown();
        executorService.awaitTermination(5L, TimeUnit.MINUTES);

//        keyValueService.put(table1, ImmutableMap.of(TABLE_CELL, TABLE_VALUE), 123L);
//        keyValueService.put(table1, ImmutableMap.of(TABLE_CELL, TABLE_VALUE), 125L);
        keyValueService.put(table1, ImmutableMap.of(TABLE_CELL, TABLE_VALUE), 127L);
        Map<Cell, Value> cellValueMap = keyValueService.get(table1, ImmutableMap.of(Cell.create(PtBytes.toBytes("row0"), PtBytes.toBytes("col0")), 130L));

        System.out.println("We got from table1 : " + cellValueMap.size());
    }

    @Ignore @Test
    public void testRunningMultipleKVSLockSync() throws Exception {
        TableReference table1 = TableReference.createFromFullyQualifiedName("ns.table1");
        int threadCount = 20;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ForkJoinPool threadPool = new ForkJoinPool(threadCount);

        AtomicInteger successes = new AtomicInteger();
        threadPool.submit(() -> {
            IntStream.range(0, threadCount).parallel().forEach(i -> {
                try {
                    KeyValueService keyValueService = CassandraKeyValueService.create(
                            CassandraKeyValueServiceConfigManager.createSimpleManager(CassandraTestSuite.CASSANDRA_KVS_CONFIG));
                    barrier.await();
                    keyValueService.createTable(table1, AtlasDbConstants.GENERIC_TABLE_METADATA);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                    // Do nothing
                }
            });
        });

        //TODO: catch the exception
        threadPool.shutdown();
        threadPool.awaitTermination(5L, TimeUnit.MINUTES);
        assertThat(successes.get(), is(threadCount));
    }

    protected void async(Callable callable) {
        executorService.submit(callable);
    }

    private void assertThatFutureDidNotSucceedYet(Future future) throws InterruptedException {
        if (future.isDone()) {
            try {
                future.get();
                throw new AssertionError("Future task should have failed but finished successfully");
            } catch (ExecutionException e) {
                // if execution is done, we expect it to have failed
            }
        }
    }

}
