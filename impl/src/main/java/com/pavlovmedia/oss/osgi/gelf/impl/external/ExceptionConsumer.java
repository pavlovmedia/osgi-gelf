package com.pavlovmedia.oss.osgi.gelf.impl.external;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This is a class that is intended to be used with a Consumer<Exception> where
 * you want to execute actions on the handled exceptions
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 * @since 1.0.0
 */
public class ExceptionConsumer {
    private final LinkedList<Exception> exceptions = new LinkedList<>();

    /**
     * This is the main method you would pass into a method that takes an
     * exception consumer
     * @since 1.0.0
     * @param e exception to handle
     */
    public void onError(final Exception e) {
        exceptions.push(e);
    }

    /**
     * True if this consumer collected any exceptions
     * @since 1.0.0
     */
    public boolean hasErrors() {
        return !exceptions.isEmpty();
    }

    /**
     * Returns the full list of exceptions
     * @since 1.0.0
     */
    public List<Exception> getExceptions() {
        return Collections.unmodifiableList(exceptions);
    }

    /**
     * This will walk all the exceptions and call the
     * logger consumer for each of the exceptions
     * @since 1.0.0
     * @param logger
     */
    public ExceptionConsumer log(final Consumer<Exception> logger) {
        exceptions.forEach(logger);
        return this;
    }

    /**
     * This method will throw a converted throwable if there are any errors. This method
     * will select the first exception on the list, which will be the first exception in the
     * queue
     * @since 1.0.0
     * @param exceptionConverter
     * @throws T
     */
    public <T extends Throwable> void andThrow(final Function<Exception,T> exceptionConverter) throws T {
        if (hasErrors()) {
            throw exceptionConverter.apply(exceptions.getFirst());
        }
    }

    /**
     * This method will throw a converted throwable if there are any errors. This method
     * will select the last exception on the list, which will be the last exception in the
     * queue
     * @since 1.0.0
     * @param exceptionConverter
     * @throws T
     */
    public <T extends Throwable> void andThrowLast(final Function<Exception,T> exceptionConverter) throws T {
        if (hasErrors()) {
            throw exceptionConverter.apply(exceptions.getLast());
        }
    }
}