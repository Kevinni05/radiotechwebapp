package com.radiotech.radiotech_backend.service;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class TenantDashboardCacheTest {
    @Test
    void summariesAreTenantScopedImmutableExpireAndDoNotCacheFailures() throws Exception {
        AtomicLong clock = new AtomicLong();
        TenantDashboardCache cache = new TenantDashboardCache(clock::get, 2);
        AtomicInteger reads = new AtomicInteger();
        var first = cache.get("a", () -> { reads.incrementAndGet(); return Map.of("tasks", 1, "taskStats", Map.of("assigned", 1)); });
        assertSame(first, cache.get("a", () -> { throw new AssertionError("Duplicate read"); }));
        assertThrows(UnsupportedOperationException.class, () -> first.put("tasks", 20));
        assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) first.get("taskStats")).clear());
        assertEquals(2, cache.get("b", () -> Map.of("tasks", 2)).get("tasks"));
        clock.set(Duration.ofSeconds(15).toNanos());
        assertEquals(3, cache.get("a", () -> { reads.incrementAndGet(); return Map.of("tasks", 3); }).get("tasks"));
        assertEquals(2, reads.get());
        cache.invalidate("a");
        assertThrows(IllegalStateException.class, () -> cache.get("a", () -> { throw new IllegalStateException("Unavailable"); }));
        assertEquals(4, cache.get("a", () -> Map.of("tasks", 4)).get("tasks"));
        assertThrows(SecurityException.class, () -> cache.get(" ", () -> Map.of()));
        cache.get("c", () -> Map.of("tasks", 5));
        assertEquals(6, cache.get("b", () -> Map.of("tasks", 6)).get("tasks")); // B was evicted.
    }

    @Test
    void concurrentReadersShareWorkAndInvalidatedInflightWorkCannotRepopulateCache() throws Exception {
        TenantDashboardCache cache = new TenantDashboardCache();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(6);
        try {
            var first = pool.submit(() -> cache.get("a", () -> {
                loads.incrementAndGet(); started.countDown();
                if (!finish.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout");
                return Map.of("tasks", 1);
            }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var second = pool.submit(() -> cache.get("a", () -> { throw new AssertionError("Duplicate work"); }));
            finish.countDown();
            assertEquals(1, first.get(5, TimeUnit.SECONDS).get("tasks"));
            assertEquals(1, second.get(5, TimeUnit.SECONDS).get("tasks"));
            assertEquals(1, loads.get());
            CountDownLatch oldStarted = new CountDownLatch(1);
            CountDownLatch oldFinish = new CountDownLatch(1);
            var old = pool.submit(() -> cache.get("b", () -> {
                oldStarted.countDown();
                if (!oldFinish.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout");
                return Map.of("tasks", 1);
            }));
            assertTrue(oldStarted.await(5, TimeUnit.SECONDS));
            cache.invalidate("b");
            assertEquals(2, cache.get("b", () -> Map.of("tasks", 2)).get("tasks"));
            oldFinish.countDown();
            assertEquals(1, old.get(5, TimeUnit.SECONDS).get("tasks"));
            assertEquals(2, cache.get("b", () -> { throw new AssertionError("Old result replaced fresh result"); }).get("tasks"));
        } finally { finish.countDown(); pool.shutdownNow(); }
    }
}
