package com.radiotech.radiotech_backend.service;

import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.LongSupplier;

/** Short-lived tenant summaries; concurrent readers share one computation. */
@Component
public class TenantDashboardCache {
    private static final Duration TTL = Duration.ofSeconds(15);
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final LongSupplier clock;
    private final int capacity;

    public TenantDashboardCache() { this(System::nanoTime, 512); }

    TenantDashboardCache(LongSupplier clock, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Invalid cache capacity");
        this.clock = clock;
        this.capacity = capacity;
    }

    private static class Entry {
        final FutureTask<Map<String, Object>> task;
        volatile long completedAt;
        Entry(Callable<Map<String, Object>> loader, LongSupplier clock) {
            task = new FutureTask<>(() -> {
                Map<String, Object> value = immutable(loader.call());
                completedAt = clock.getAsLong();
                return value;
            });
        }
    }

    public Map<String, Object> get(String tenant, Callable<Map<String, Object>> loader) throws Exception {
        if (tenant == null || tenant.isBlank()) throw new SecurityException("Tenant required");
        Entry entry;
        synchronized (entries) {
            entry = entries.get(tenant);
            if (entry == null || entry.task.isDone() && clock.getAsLong() - entry.completedAt >= TTL.toNanos()) {
                entry = new Entry(loader, clock);
                entries.put(tenant, entry);
                if (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
            }
        }
        entry.task.run(); // FutureTask runs once, outside the cache lock.
        try {
            return entry.task.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (ExecutionException failed) {
            synchronized (entries) { entries.remove(tenant, entry); }
            if (failed.getCause() instanceof Exception cause) throw cause;
            throw new IllegalStateException("Dashboard computation failed", failed.getCause());
        }
    }

    public void invalidate(String tenant) {
        if (tenant == null || tenant.isBlank()) return;
        synchronized (entries) { entries.remove(tenant); }
    }

    private static Map<String, Object> immutable(Map<String, Object> value) {
        Map<String, Object> copy = new LinkedHashMap<>();
        value.forEach((key, item) -> copy.put(key, item instanceof Map<?, ?> nested ? Map.copyOf(nested) : item));
        return Map.copyOf(copy);
    }
}
