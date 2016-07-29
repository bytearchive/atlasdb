/**
 * Copyright 2015 Palantir Technologies
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
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableMap;
import com.palantir.atlasdb.AtlasDbConstants;
import com.palantir.atlasdb.cassandra.CassandraKeyValueServiceConfigManager;
import com.palantir.atlasdb.cassandra.ImmutableCassandraKeyValueServiceConfig;
import com.palantir.atlasdb.encoding.PtBytes;
import com.palantir.atlasdb.keyvalue.api.Cell;
import com.palantir.atlasdb.keyvalue.api.TableReference;
import com.palantir.atlasdb.keyvalue.api.Value;

abstract public class AbstractCassandraLockTest {
    protected static final long GLOBAL_DDL_LOCK_NEVER_ALLOCATED_VALUE = Long.MAX_VALUE - 1;
    private final Cell TABLE_CELL = Cell.create(PtBytes.toBytes("row0"), PtBytes.toBytes("col0"));
    private final byte[] TABLE_VALUE = PtBytes.toBytes("xyz");
    protected CassandraKeyValueService kvs;
    protected CassandraKeyValueService slowTimeoutKvs;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    public static final TableReference BAD_TABLE = TableReference.createFromFullyQualifiedName("foo.b@r");
    public static final TableReference GOOD_TABLE = TableReference.createFromFullyQualifiedName("foo.bar");

    @Before
    public void setUp() {
        ImmutableCassandraKeyValueServiceConfig quickTimeoutConfig = CassandraTestSuite.CASSANDRA_KVS_CONFIG
                .withSchemaMutationTimeoutMillis(500);
        kvs = CassandraKeyValueService.create(
                CassandraKeyValueServiceConfigManager.createSimpleManager(quickTimeoutConfig));

        ImmutableCassandraKeyValueServiceConfig slowTimeoutConfig = CassandraTestSuite.CASSANDRA_KVS_CONFIG
                .withSchemaMutationTimeoutMillis(60 * 1000);
        slowTimeoutKvs = CassandraKeyValueService.create(
                CassandraKeyValueServiceConfigManager.createSimpleManager(slowTimeoutConfig));

        kvs.dropTable(AtlasDbConstants.TIMESTAMP_TABLE);
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @After
    public void tearDown() {
        kvs.teardown();
    }

    @Ignore
    @Test
    public void testLockAndUnlockWithoutContention() {
        long ourId = kvs.waitForSchemaMutationLock();
        kvs.schemaMutationUnlock(ourId);
    }

    @Ignore
    @Test
    public void testOnlyOneLockCanBeLockedAtATime() throws InterruptedException, ExecutionException, TimeoutException {
        long firstLock = kvs.waitForSchemaMutationLock();

        Future tryToAcquireSecondLock = async(() -> kvs.waitForSchemaMutationLock());

        Thread.sleep(3 * 1000);
        assertThatFutureDidNotSucceedYet(tryToAcquireSecondLock);

        tryToAcquireSecondLock.cancel(true);
        kvs.schemaMutationUnlock(firstLock);
    }

    @Ignore
    @Test
    public void testUnlockIsSuccessful() throws InterruptedException, TimeoutException, ExecutionException {
        long id = kvs.waitForSchemaMutationLock();
        Future future = async(() -> {
            long newId = kvs.waitForSchemaMutationLock();
            kvs.schemaMutationUnlock(newId);
        });
        Thread.sleep(100);
        Assert.assertFalse(future.isDone());
        kvs.schemaMutationUnlock(id);
        future.get(3, TimeUnit.SECONDS);
    }

    @Ignore
    @Test (timeout = 10 * 1000)
    public void testTableCreationCanOccurAfterError() {
        try {
            kvs.createTable(BAD_TABLE, AtlasDbConstants.GENERIC_TABLE_METADATA);
        } catch (Exception e) {
            // failure expected
        }
        kvs.createTable(GOOD_TABLE, AtlasDbConstants.GENERIC_TABLE_METADATA);
        kvs.dropTable(GOOD_TABLE);
    }

    @Ignore @Test
    public void testCreatingTableWorksAfterClockfortsStuff() {
        TableReference tr = TableReference.createFromFullyQualifiedName("foo.barbaz");
        try {
            kvs.createTable(tr, AtlasDbConstants.GENERIC_TABLE_METADATA);
        } finally {
            kvs.dropTable(tr);
        }
    }

    @Test
    public void testCreatingMultipleTablesAtOnceLessThreads() throws InterruptedException {
        int successes = creatingMultipleTablesAtOnce(20);
        assertThat(successes, is(20));
        Map<Cell, Value> cellValueMap = putAndGetData();
        assertThat(cellValueMap, hasKey(TABLE_CELL));
        kvs.dropTable(GOOD_TABLE);
    }

    @Test
    public void testCreatingMultipleTablesAtOnceMoreThreads() throws InterruptedException {
        int successes = creatingMultipleTablesAtOnce(60);
        assertThat(successes, is(60));
        Map<Cell, Value> cellValueMap = putAndGetData();
        assertThat(cellValueMap, hasKey(TABLE_CELL));
        kvs.dropTable(GOOD_TABLE);
    }

    private int creatingMultipleTablesAtOnce(int threadCount) throws InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ForkJoinPool threadPool = new ForkJoinPool(threadCount);

        AtomicInteger successes = new AtomicInteger();
        threadPool.submit(() -> {
            IntStream.range(0, threadCount).parallel().forEach(i -> {
                try {
                    barrier.await();

                    slowTimeoutKvs.createTable(GOOD_TABLE, AtlasDbConstants.GENERIC_TABLE_METADATA);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                    // Do nothing
                }
            });
        });

        threadPool.shutdown();
        threadPool.awaitTermination(2L, TimeUnit.MINUTES);
        return successes.get();
    }

    private Map<Cell, Value> putAndGetData() {
        slowTimeoutKvs.put(GOOD_TABLE, ImmutableMap.of(TABLE_CELL, TABLE_VALUE), 123L);
        return slowTimeoutKvs.get(GOOD_TABLE, ImmutableMap.of(Cell.create(PtBytes.toBytes("row0"), PtBytes.toBytes("col0")), 130L));
    }

    protected Future async(Runnable callable) {
        return executorService.submit(callable);
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

    @Ignore
    @Test
    public void describeVersionBehavesCorrectly() throws Exception {
        kvs.clientPool.runWithRetry(CassandraVerifier.underlyingCassandraClusterSupportsCASOperations);
    }
}
