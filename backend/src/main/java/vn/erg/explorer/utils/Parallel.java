package vn.erg.explorer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Maps a list with one virtual thread per item. Safe to nest: the number of node calls in flight
 * is bounded in {@link vn.erg.explorer.node.NodeClient}, not here, so an outer map never starves an inner one.
 */
public final class Parallel {

    private Parallel() {
    }

    public static <T, R> List<R> map(List<T> items, Function<T, R> fn) {
        if (items.size() <= 1) {
            // mutable like the parallel path (callers sort the result)
            List<R> out = new ArrayList<>(1);
            for (T item : items) {
                out.add(fn.apply(item));
            }
            return out;
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<R>> futures = new ArrayList<>(items.size());
            for (T item : items) {
                futures.add(executor.submit(() -> fn.apply(item)));
            }
            List<R> out = new ArrayList<>(items.size());
            for (Future<R> f : futures) {
                out.add(f.get());
            }
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        }
    }

}
