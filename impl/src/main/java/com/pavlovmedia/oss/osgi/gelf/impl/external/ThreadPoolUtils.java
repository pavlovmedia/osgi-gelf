package com.pavlovmedia.oss.osgi.gelf.impl.external;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Note: This class was copy-pasta from a different pavlov project
 * Thread helpers
 */
public final class ThreadPoolUtils {
    private ThreadPoolUtils() { }

    public static ThreadFactory getThreadFactory(final String name, final AtomicInteger counter) {
        Objects.requireNonNull(name, "name is Null");
        Objects.requireNonNull(counter, "counter is Null");
        return r -> {
           String threadName = String.format("%s-%d", name, counter.incrementAndGet());
           Thread ret = new Thread(r);
           ret.setName(threadName);
           return ret;
        };
     }

     public static ThreadFactory getThreadFactory(final String name, final AtomicInteger counter, final BiConsumer<Thread, Throwable> uncaughtHandler) {
        Objects.requireNonNull(name, "name is Null");
        Objects.requireNonNull(counter, "counter is Null");
        Objects.requireNonNull(uncaughtHandler, "uncaughtHandler is Null");
        return r -> {
           String threadName = String.format("%s-%d", name, counter.incrementAndGet());
           Thread ret = new Thread(r);
           ret.setUncaughtExceptionHandler((t, e) -> {
              uncaughtHandler.accept(t, e);
           });
           ret.setName(threadName);
           return ret;
        };
     }
}