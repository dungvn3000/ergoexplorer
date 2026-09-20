package vn.erg.explorer.utils;

import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A single value recomputed after it expires. Unlike a cache loader, the supplier runs outside any
 * map lock (a blocking node call inside {@code ConcurrentHashMap.compute} pins virtual threads and
 * can deadlock), and a {@link ReentrantLock} ensures only one thread recomputes at a time.
 */
public final class Memo<T> {

    private final Supplier<T> supplier;
    private final long ttlNanos;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile T value;
    private volatile long expiresAt;

    public Memo(Duration ttl, Supplier<T> supplier) {
        this.supplier = supplier;
        this.ttlNanos = ttl.toNanos();
    }

    public T get() {
        T v = value;
        if (v != null && System.nanoTime() < expiresAt) {
            return v;
        }
        lock.lock();
        try {
            v = value;
            if (v != null && System.nanoTime() < expiresAt) {
                return v;
            }
            v = supplier.get();
            value = v;
            expiresAt = System.nanoTime() + ttlNanos;
            return v;
        } finally {
            lock.unlock();
        }
    }

}
