package com.pavlovmedia.oss.osgi.gelf.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
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
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyUnbounded;
import org.apache.felix.scr.annotations.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pavlovmedia.oss.osgi.gelf.impl.external.IronValueHelper;
import com.pavlovmedia.oss.osgi.gelf.impl.external.ThreadPoolUtils;
import com.pavlovmedia.oss.osgi.gelf.lib.GelfMessage;
import com.pavlovmedia.oss.osgi.gelf.lib.IGelfTransporter;

/**
 * PavlovGelfTcpTransporter
 */
@Component(metatype = true, policy = ConfigurationPolicy.REQUIRE, immediate = true)
@Service(value = IGelfTransporter.class)
@Properties({
        @Property(
            name = PavlovGelfTcpTransporter.GRAYLOG_ACTIVE,
            boolValue = false,
            label = "Active",
            description = "Graylog2 Active"),
        @Property(
            name = PavlovGelfTcpTransporter.GRAYLOG_HOST,
            label = "Host",
            description = "Graylog2 Target Host"),
        @Property(
            name = PavlovGelfTcpTransporter.GRAYLOG_PORT,
            intValue = PavlovGelfTcpTransporter.GRAYLOG_PORT_DEFAULT,
            label = "Port",
            description = "Graylog2 Port"),
        @Property(
            name = PavlovGelfTcpTransporter.GRAYLOG_LOG_CONSOLE,
            boolValue = false,
            label = "Console Messages",
            description = "Log messages to the console"),
        @Property(
            name = PavlovGelfTcpTransporter.GRAYLOG_ADD_FIELDS,
            value = "",
            unbounded = PropertyUnbounded.VECTOR,
            label = "Additional Fields",
            description = "Additional fields to add to the record in key:value pairs"),
        @Property(
            name = PavlovGelfTcpTransporter.GRAYLOG_THREAD_POOL_SIZE,
            intValue = PavlovGelfTcpTransporter.GRAYLOG_THREAD_POOL_SIZE_DEFAULT,
            label = "thread Pool size",
            description = "message processing thread pool size (minimum 10)"),
        @Property(
                name=PavlovGelfTcpTransporter.GRAYLOG_HOSTNAME, 
                label="Source hostname", 
                description="If non-empty, this will be used as the hostname in logging messages")
        })
public class PavlovGelfTcpTransporter implements IGelfTransporter {
    static final String GRAYLOG_ACTIVE = "graylog.active";
    static final String GRAYLOG_HOST = "graylog.host";
    static final String GRAYLOG_LOG_CONSOLE = "graylog.console";
    static final String GRAYLOG_ADD_FIELDS = "graylog.additional.fields";

    static final String GRAYLOG_PORT = "graylog.port";
    static final int GRAYLOG_PORT_DEFAULT = 12201;

    static final String GRAYLOG_THREAD_POOL_SIZE = "graylog.poolSize";
    static final int GRAYLOG_THREAD_POOL_SIZE_DEFAULT = 10;
    
    static final String GRAYLOG_HOSTNAME = "source.hostname";

    static final int GRAYLOG_SLEEP_DEFAULT_IN_MILLIS = 1000;

    private static String _HOSTNAME;
    
    private final ObjectMapper mapper = new ObjectMapper();

    private AtomicBoolean active = new AtomicBoolean(false);
    private AtomicBoolean consoleMessages = new AtomicBoolean(false);
    private String hostname;
    private int port;
    private Map<String, String> additionalFields = Collections.emptyMap();

    private final Object socketLock = new Object();
    private Optional<Socket> transport = Optional.empty();
    private Optional<OutputStream> outputStream = Optional.empty();

    private ExecutorService executorService;

    private Future<?> processGelfMessageFuture;
    private LinkedBlockingQueue<GelfMessage> gelfMessageQueue = new LinkedBlockingQueue<>();
    private AtomicBoolean gelfMessageProcessingActive = new AtomicBoolean(false);

    /**
     * Can set a hostname for this sink
     * @param hostname
     */
    public static void setHostname(final String hostname) {
        if (Objects.nonNull(hostname) && !hostname.trim().isEmpty()) {
            _HOSTNAME = hostname.trim();
        } else {
            _HOSTNAME = null;
        }
    }
    
    /**
     * This method will determine the hostname and then cache it
     * for future use.
     * @return the hostname of the system
     */
    public static String getHostname() {
        if (null == _HOSTNAME) {
            try {
                _HOSTNAME = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                System.err.println("Failed to find hostname "+e.getMessage());
                _HOSTNAME = "Unknown";
            }
        }
        return _HOSTNAME;
    }

    @Override
    public void setLoggedAsHostname(String hostname) {
        setHostname(hostname);
    }
    
    @Activate
    protected void activate(final Map<String, Object> config) {
        IronValueHelper helper = new IronValueHelper(config);

        // Initialize service parameters
        initializeService(helper);

        // Initialize socket parameters
        if (active.get()) {
            trace("Enabling GELF logging to %s:%d", hostname, port);
            initializeSocket();
        }

        // Initialize threading parameters
        transport.ifPresent(t -> initializeThreading(helper));
    }

    @Modified
    protected void modified(final Map<String, Object> config) {
        IronValueHelper helper = new IronValueHelper(config);

        // Terminate service
        terminateService();

        // Terminate threading
        terminateThreading();

        // Initialize service parameters
        initializeService(helper);

        // Initialize threading parameters
        transport.ifPresent(t -> initializeThreading(helper));
    }

    @Deactivate
    protected void deactivate() {
        // Terminate service
        terminateService();

        // Terminate threading
        terminateThreading();

        // Terminate socket
        terminateSocket();
    }


    /**
     * Initialize any properties that are used by the service itself
     *
     * @param helper  contains all Felix properties defined for this service
     */
    private void initializeService(final IronValueHelper helper) {
        consoleMessages.set(helper.getBoolean(GRAYLOG_LOG_CONSOLE).orElse(false));

        // Check for the active flag that denotes GELF should be enabled
        active.set(helper.getBoolean(GRAYLOG_ACTIVE).orElse(false));

        if (active.get()) {
            // See if we have additional fields to pass along
            List<String> graylogFields = helper.getStringList(GRAYLOG_ADD_FIELDS);
            additionalFields = graylogFields.stream().map(e -> e.split(":")).filter(a -> a.length == 2)
                    .collect(Collectors.toMap(a -> a[0].trim(), a -> a[1].trim()));

            // Check to see if we have a host, if not the rest doesn't matter
            Optional<String> oHostName = helper.getString(GRAYLOG_HOST);

            if (!oHostName.isPresent()) {
                trace("Cannot start gelf bundle, a host is not configured.");
                active.set(false);
            } else {
                hostname = oHostName.get();
                port = helper.getInteger(GRAYLOG_PORT).orElse(GRAYLOG_PORT_DEFAULT);
            }
            
            helper.getString(GRAYLOG_HOSTNAME).ifPresent(PavlovGelfTcpTransporter::setHostname);
        }
    }


    /**
     * Initialize socket operations if needed
     */
    private void initializeSocket() {
        synchronized (socketLock) {
            if (!transport.isPresent()) {
                try {
                    InetAddress address = InetAddress.getByName(hostname);
                    Socket trans = new Socket(address, port);
                    trans.setSoTimeout(500);
                    trans.shutdownInput();
                    transport = Optional.of(trans);
                    outputStream = Optional.of(trans.getOutputStream());

                    trace("GELF logging connection succeeded to %s:%d", hostname, port);
                } catch (IOException e) {
                    trace("GELF logging failed to connect to %s:%d => %s", hostname, port, e.getMessage());

                    transport = Optional.empty();
                    outputStream = Optional.empty();
                }
            }
        }
    }

    /**
     * Initialize any properties that are used by the threading mechanism
     *
     * @param helper  contains all Felix properties defined for this service
     */
    private void initializeThreading(final IronValueHelper helper) {
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


    /**
     * Set any properties that used by the service itself to allow for terminsation
     */
    private void terminateService() {
        active.set(false);
    }

    /**
     * Terminate socket operations
     */
    private void terminateSocket() {
        synchronized (socketLock) {
            transport.ifPresent(trans -> {
                trace("Shutting down GELF logging");

                try {
                    trans.close();
                } catch (IOException e) {
                    /* Do nothing */ }

                transport = Optional.empty();
                outputStream = Optional.empty();
            });
        }
    }

    /**
     * Terminate threading
     */
    private void terminateThreading() {
        gelfMessageProcessingActive.set(false);

        if (Objects.nonNull(processGelfMessageFuture)) {
            processGelfMessageFuture.cancel(true);
        }

        if (null != executorService && !executorService.isShutdown()) {
            executorService.shutdown();
            executorService = null;
        }
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

        // Set the hostname as a last resort if we didn't get one passed in
        if (Objects.isNull(message.host) || message.host.trim().isEmpty()) {
            message.host = getHostname();
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
        if (!active.get()) {
            return; // We aren't running
        }


        List<GelfMessage> gelfMessages = new ArrayList<>();

        while (gelfMessageProcessingActive.get()) {
            try {
                gelfMessages.clear();
                gelfMessages.add(gelfMessageQueue.take()); // This waits until an element is available

                gelfMessageQueue.drainTo(gelfMessages);

                gelfMessages.forEach(this::processGelfMessage);
            } catch (InterruptedException e) {
                // NOOP
            }
        }
    }


    /**
     * Write a message to Gelf
     *
     * @param message message to send via GELF
     */
    private void processGelfMessage(final GelfMessage message) {
        if (!active.get()) {
            return; // We aren't running
        }


        if (!additionalFields.isEmpty()) {
            message.additionalFields.putAll(additionalFields);
        }

        synchronized (socketLock) {
            initializeSocket();

            if (!transport.isPresent()) {
                // Re-queue the message
                try {
                    Thread.sleep(GRAYLOG_SLEEP_DEFAULT_IN_MILLIS);
                } catch (InterruptedException e) {
                    // NOOP
                }

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
                            terminateSocket();

                            trace("Failed to serialize message, re-queueing message due to -- %s", e.getMessage());

                            // Re-queue the message
                            logGelfMessage(message);
                        }
                    });
                });
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