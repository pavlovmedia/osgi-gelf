package com.pavlovmedia.oss.osgi.gelf.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pavlovmedia.oss.osgi.gelf.impl.external.ExceptionConsumer;
import com.pavlovmedia.oss.osgi.gelf.impl.external.IronValueHelper;
import com.pavlovmedia.oss.osgi.gelf.impl.external.ThreadPoolUtils;
import com.pavlovmedia.oss.osgi.gelf.lib.GelfMessage;
import com.pavlovmedia.oss.osgi.gelf.lib.IGelfTransporter;


/**
 * This is the underlying transport for GELF messages. It can be used on its own with the
 * log sink disabled, or used as the configuration to the log sink itself.
 *
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
// @Component(metatype=true, policy=ConfigurationPolicy.REQUIRE, immediate=true)
// @Service(value=IGelfTransporter.class)
// @Properties({
//     @Property(
//         name=PavlovGelfTcpTransporter.GRAYLOG_ACTIVE,
//         boolValue=false,
//         label="Active",
//         description="Graylog2 Active"),
//     @Property(
//         name=PavlovGelfTcpTransporter.GRAYLOG_HOST,
//         label="Host",
//         description="Graylog2 Target Host"),
//     @Property(
//         name=PavlovGelfTcpTransporter.GRAYLOG_PORT,
//         intValue=12201,
//         label="Port",
//         description="Graylog2 Port"),
//     @Property(
//         name=PavlovGelfTcpTransporter.GRAYLOG_LOG_CONSOLE,
//         boolValue=false,
//         label="Console Messages",
//         description="Log messages to the console"),
//     @Property(
//         name=PavlovGelfTcpTransporter.GRAYLOG_ADD_FIELDS,
//         value="",
//         unbounded=PropertyUnbounded.VECTOR,
//         label = "Additional Fields",
//         description = "Additional fields to add to the record in key:value pairs"),
//     @Property(
//         name=PavlovGelfTcpTransporter.GRAYLOG_THREAD_POOL_SIZE,
//         intValue=PavlovGelfTcpTransporter.GRAYLOG_THREAD_POOL_SIZE_DEFAULT,
//         label="thread Pool size",
//         description="message processing thread pool size (minimum 10)")
// })


public class PavlovGelfTcpTransporter implements IGelfTransporter {
    static final String GRAYLOG_ACTIVE="graylog.active";
    static final String GRAYLOG_HOST="graylog.host";
    static final String GRAYLOG_PORT="graylog.port";
    static final String GRAYLOG_LOG_CONSOLE = "graylog.console";
    static final String GRAYLOG_ADD_FIELDS="graylog.additional.fields";
    static final String GRAYLOG_THREAD_POOL_SIZE="graylog.poolSize";

    static final int GRAYLOG_THREAD_POOL_SIZE_DEFAULT=10;

    private final ObjectMapper mapper = new ObjectMapper();

    private AtomicBoolean active = new AtomicBoolean(false);
    private String hostname;
    private int port;
    private AtomicBoolean consoleMessages = new AtomicBoolean(false);

    private final Object socketLock = new Object();
    private Optional<Socket> transport = Optional.empty();
    private Optional<OutputStream> outputStream = Optional.empty();
    private Map<String,String> additionalFields = Collections.emptyMap();

    private ExecutorService executorService;

    private Future<?> processGelfMessageFuture;
    private LinkedBlockingQueue<GelfMessage> gelfMessageQueue = new LinkedBlockingQueue<>();
    private AtomicBoolean gelfMessageProcessingActive = new AtomicBoolean(false);


    @Activate
    protected void activate(final Map<String, Object> config) {
        osgiSetup(config);
    }


    @Modified
    protected void modified(final Map<String, Object> config) {
        osgiSetup(config);
    }


    @Deactivate
    protected void deactivate() {
        disconnect();

        gelfMessageProcessingActive.set(false);

        if (!Objects.isNull(processGelfMessageFuture)) {
            processGelfMessageFuture.cancel(true);
        }

        if (null != executorService && !executorService.isShutdown()) {
            executorService.shutdown();
            executorService = null;
        }
    }


    /**
     * Since the activator and modifier do the same thing, just share this code
     *
     * @param config the map from activate or modified
     */
    private void osgiSetup(final Map<String, Object> config) {
        IronValueHelper helper = new IronValueHelper(config);

        consoleMessages.set(helper.getBoolean(GRAYLOG_LOG_CONSOLE).orElse(false));


        // See if we have additional fields to pass along
        List<String> graylogFields = helper.getStringList(GRAYLOG_ADD_FIELDS);
        additionalFields = graylogFields.stream()
                .map(e -> e.split(":"))
                .filter(a -> a.length == 2)
                .collect(Collectors.toMap(a -> a[0].trim(), a -> a[1].trim()));


        // Check to see if we have a host, if not the rest doesn't matter
        Optional<String> glHost = helper.getString(GRAYLOG_HOST);

        if (!glHost.isPresent()) {
            trace("Cannot start gelf bundle, a host is not configured.");
            active.set(false);
            disconnect();
        } else {
            String newHostname = glHost.get();
            Integer newPort = helper.getInteger(GRAYLOG_PORT).orElse(12201);

            /**
             * If either of these changed, we need to close the current
             * connection.
             */
            if (!(newHostname.equals(hostname) && newPort.equals(port))) {
                hostname = newHostname;
                port = newPort;
                disconnect();
            }

            active.set(helper.getBoolean(GRAYLOG_ACTIVE).orElse(false));

            if (active.get()) {
                trace("Enabling GELF logging to %s:%d", hostname, port);
                ensureConnection(e -> { });
            } else {
                disconnect();
            }
        }

        if (active.get()) {
            if (null == executorService) {
                int threadPoolSize = helper.getInteger(GRAYLOG_THREAD_POOL_SIZE).orElse(GRAYLOG_THREAD_POOL_SIZE_DEFAULT);
                threadPoolSize = Math.max(threadPoolSize, GRAYLOG_THREAD_POOL_SIZE_DEFAULT);

                executorService = Executors.newFixedThreadPool(
                    threadPoolSize,
                    ThreadPoolUtils.getThreadFactory("PavlovGelfTcpTransporter", new AtomicInteger(0)));

                gelfMessageProcessingActive.set(true);
                processGelfMessageFuture = executorService.submit(this::processGelfMessageQueue);
            }
        }
    }


    /**
     * Disconnect from the transport
     */
    private void disconnect() {
        synchronized (socketLock) {
            transport.ifPresent(trans -> {
                trace("Shutting down GELF logging");

                try {
                    trans.close();
                } catch (IOException e) { /* Do nothing */ }

                transport = Optional.empty();
                outputStream = Optional.empty();
            });
        }

        // gelfMessageProcessingActive.set(false);

        // if (!Objects.isNull(processGelfMessageFuture)) {
        //     processGelfMessageFuture.cancel(true);
        // }

        // if (null != executorService && !executorService.isShutdown()) {
        //     executorService.shutdown();
        //     executorService = null;
        // }
    }


    @Override
    public void logGelfMessage(final GelfMessage message) {
        logGelfMessage(message, e -> { });
    }


    @Override
    public void logGelfMessage(final GelfMessage message, final Consumer<IOException> onException) {
        if (!active.get()) {
            return; // We aren't running
        }

        // Add the event to the queue
        if (!gelfMessageQueue.offer(message)) {
            trace("No space available to queue Gelf Message '%s' at timestamp '%d'.", message.short_message, message.timestamp);
        }
    }


    /**
     * This is the background thread to process the Gelf Message queue
     */
    private void processGelfMessageQueue() {
        List<GelfMessage> gelfMessages = new ArrayList<>();

        while (gelfMessageProcessingActive.get()) {
            try {
                gelfMessages.clear();
                gelfMessages.add(gelfMessageQueue.take()); // This waits until an element is available

                gelfMessageQueue.drainTo(gelfMessages);

                trace("GelfMessages Size: %s -- %d", LocalDateTime.now().toString(), gelfMessages.size());

                gelfMessages.forEach(this::processGelfMessage);
            } catch (InterruptedException e) {
                // NOOP
            }
        }
    }


    // Write a message to Gelf
    private void processGelfMessage(final GelfMessage message) {
        if (!additionalFields.isEmpty()) {
            message.additionalFields.putAll(additionalFields);
        }

        trace("GELF Message -- %s", message.full_message);

        synchronized (socketLock) {
            ExceptionConsumer exceptionConsumer = new ExceptionConsumer();
            ensureConnection(exceptionConsumer::onError);
            if (exceptionConsumer.hasErrors()) {
                // Re-queue the message
                logGelfMessage(message);
            } else {
                transport.ifPresent(trans -> {
                    outputStream.ifPresent(os -> {
                        try {
                            byte[] messageBytes = mapper.writeValueAsBytes(message);
                            os.write(messageBytes);
                            // There is a bug in GELF that requires us to end with a null byte
                            os.write(new byte[] { '\0' });
                        } catch (IOException e) {
                            // Be sure to drop the connection so we get reconnected
                            disconnect();

                            trace("Failed to serialize message, re-queueing message due to -- %s", e.getMessage());
                            // e.printStackTrace();

                            // Re-queue the message
                            logGelfMessage(message);
                        }
                    });
                });
            }
        }
    }


    /**
     * Makes sure we have an active connection
     */
    private void ensureConnection(final Consumer<Exception> onError) {
        synchronized (socketLock) {
            if (!transport.isPresent()) {
                try {
                    trace("Preparing the output stream for GELF logging");

                    InetAddress address = InetAddress.getByName(hostname);
                    Socket trans = new Socket(address, port);
                    trans.setSoTimeout(500);
                    trans.shutdownInput();
                    transport = Optional.of(trans);
                    outputStream = Optional.of(trans.getOutputStream());

                    trace("GELF logging operational");
                } catch (IOException e) {
                    trace("GELF logging failed to connect to %s:%d => %s", hostname, port, e.getMessage());

                    transport = Optional.empty();
                    outputStream = Optional.empty();

                    onError.accept(e);
                }
            }
        }
    }


    /**
     * Writes a potentially formatted message to the console, if enabled
     *
     * @param format String format, like String.format
     * @param args Argument list, like String.format
     */
    private void trace(final String format, final Object...args) {
        if (consoleMessages.get()) {
            System.out.println(String.format(format, args));
        }
    }
}
