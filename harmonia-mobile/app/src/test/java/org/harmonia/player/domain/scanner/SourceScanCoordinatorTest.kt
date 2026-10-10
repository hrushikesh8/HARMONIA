package org.harmonia.player.domain.scanner

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SourceScanCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `1 two callers cannot scan the same source concurrently`() = runTest(testDispatcher) {
        val coordinator = SourceScanCoordinator()
        val caller1Holding = CompletableDeferred<Unit>()
        val caller1Release = CompletableDeferred<Unit>()
        val caller2Completed = CompletableDeferred<Unit>()

        val concurrentExecutions = AtomicInteger(0)
        var maxConcurrent = 0

        // Caller 1 starts and holds lock
        val job1 = launch {
            coordinator.withSourceLock("source_a") {
                val current = concurrentExecutions.incrementAndGet()
                if (current > maxConcurrent) maxConcurrent = current
                caller1Holding.complete(Unit)
                caller1Release.await()
                concurrentExecutions.decrementAndGet()
            }
        }

        advanceUntilIdle()
        assertTrue(caller1Holding.isCompleted)
        assertTrue(coordinator.isSourceScanning("source_a"))

        // Caller 2 requests lock on same source
        val job2 = launch {
            coordinator.withSourceLock("source_a") {
                val current = concurrentExecutions.incrementAndGet()
                if (current > maxConcurrent) maxConcurrent = current
                concurrentExecutions.decrementAndGet()
                caller2Completed.complete(Unit)
            }
        }

        advanceUntilIdle()
        // Caller 2 must be waiting because caller 1 has not released
        assertFalse("Caller 2 must not complete while caller 1 holds lock", caller2Completed.isCompleted)
        assertEquals("Max concurrent executions on source_a must be 1", 1, maxConcurrent)

        // Release caller 1
        caller1Release.complete(Unit)
        advanceUntilIdle()

        assertTrue("Caller 2 must complete after caller 1 releases lock", caller2Completed.isCompleted)
        assertEquals(1, maxConcurrent)
        assertFalse(coordinator.isSourceScanning("source_a"))

        job1.join()
        job2.join()
    }

    @Test
    fun `2 scans of different source IDs can execute concurrently`() = runTest(testDispatcher) {
        val coordinator = SourceScanCoordinator()
        val ready = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val concurrentCount = AtomicInteger(0)
        var maxConcurrent = 0

        val jobA = launch {
            coordinator.withSourceLock("source_a") {
                val c = concurrentCount.incrementAndGet()
                if (c > maxConcurrent) maxConcurrent = c
                if (c == 2) ready.complete(Unit)
                release.await()
                concurrentCount.decrementAndGet()
            }
        }

        val jobB = launch {
            coordinator.withSourceLock("source_b") {
                val c = concurrentCount.incrementAndGet()
                if (c > maxConcurrent) maxConcurrent = c
                if (c == 2) ready.complete(Unit)
                release.await()
                concurrentCount.decrementAndGet()
            }
        }

        advanceUntilIdle()
        assertTrue("Both sources should run concurrently", ready.isCompleted)
        assertEquals(2, maxConcurrent)
        assertTrue(coordinator.isSourceScanning("source_a"))
        assertTrue(coordinator.isSourceScanning("source_b"))

        release.complete(Unit)
        advanceUntilIdle()

        assertFalse(coordinator.isSourceScanning("source_a"))
        assertFalse(coordinator.isSourceScanning("source_b"))

        jobA.join()
        jobB.join()
    }

    @Test
    fun `3 cancellation of active caller releases coordination lock`() = runTest(testDispatcher) {
        val coordinator = SourceScanCoordinator()
        val caller1Holding = CompletableDeferred<Unit>()
        val caller2Completed = CompletableDeferred<Unit>()

        val job1 = launch {
            coordinator.withSourceLock("source_c") {
                caller1Holding.complete(Unit)
                delay(Long.MAX_VALUE) // Suspends until cancelled
            }
        }

        advanceUntilIdle()
        assertTrue(caller1Holding.isCompleted)
        assertTrue(coordinator.isSourceScanning("source_c"))

        val job2 = launch {
            coordinator.withSourceLock("source_c") {
                caller2Completed.complete(Unit)
            }
        }

        advanceUntilIdle()
        assertFalse(caller2Completed.isCompleted)

        // Cancel caller 1
        job1.cancelAndJoin()
        advanceUntilIdle()

        assertTrue("Caller 2 must acquire lock after caller 1 cancellation", caller2Completed.isCompleted)
        assertFalse(coordinator.isSourceScanning("source_c"))

        job2.join()
    }

    @Test
    fun `4 failure and exception in active caller releases coordination lock`() = runTest(testDispatcher) {
        val coordinator = SourceScanCoordinator()

        var caughtException = false
        try {
            coordinator.withSourceLock("source_d") {
                throw IllegalStateException("Disk I/O error during scan")
            }
        } catch (_: IllegalStateException) {
            caughtException = true
        }

        assertTrue(caughtException)
        assertFalse("Lock must be released even after exception", coordinator.isSourceScanning("source_d"))

        // Subsequent caller must be able to acquire lock immediately
        var caller2Ran = false
        coordinator.withSourceLock("source_d") {
            caller2Ran = true
        }

        assertTrue(caller2Ran)
    }
}
