package com.pavlovmedia.oss.osgi.gelf.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
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
import com.pavlovmedia.oss.osgi.gelf.lib.GelfMessage;
import com.pavlovmedia.oss.osgi.gelf.lib.IGelfTransporter;

/**
 * This is the underlying transport for GELF messages. It can be used on its own with the
 * log sink disabled, or used as the configuration to the log sink itself.
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
@Component(metatype=true, policy=ConfigurationPolicy.REQUIRE)
@Service(value=IGelfTransporter.class)
@Properties({
    @Property(name=PavlovGelfTcpTransporter.GRAYLOG_ACTIVE, boolValue=false, label="Active", description="Graylog2 Active"),
    @Property(name=PavlovGelfTcpTransporter.GRAYLOG_HOST, label="Host", description="Graylog2 Target Host"),
    @Property(name=PavlovGelfTcpTransporter.GRAYLOG_PORT, intValue=12201, label="Port", description="Graylog2 Port"),
    @Property(name=PavlovGelfTcpTransporter.GRAYLOG_LOG_CONSOLE, boolValue=false, label="Console Messages", description="Log messages to the console"),
    @Property(name=PavlovGelfTcpTransporter.GRAYLOG_ADD_FIELDS, value="", unbounded = PropertyUnbounded.VECTOR, label = "Additional Fields", description = "Additional fields to add to the record in key:value pairs")
})
public class PavlovGelfTcpTransporter implements IGelfTransporter {
    static final String GRAYLOG_ACTIVE="graylog.active";
    static final String GRAYLOG_HOST="graylog.host";
    static final String GRAYLOG_PORT="graylog.port";
    static final String GRAYLOG_LOG_CONSOLE = "graylog.console";
    static final String GRAYLOG_ADD_FIELDS="graylog.additional.fields";
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    private AtomicBoolean active = new AtomicBoolean(false);
    private String hostname;
    private int port;
    private AtomicBoolean consoleMessages = new AtomicBoolean(false);
    
    private final Object socketLock = new Object();
    private Optional<Socket> transport = Optional.empty();
    private Optional<OutputStream> outputStream = Optional.empty();
    private Map<String,String> additionalFields = Collections.emptyMap();
    
    @Override
    public void logGelfMessage(final GelfMessage message) {
        logGelfMessage(message, (e) -> { 
            System.err.println("Failed to serialize message: "+message+" "+e.getMessage()); 
            e.printStackTrace();
            });
    }
    
    @Override
    public void logGelfMessage(final GelfMessage message, final Consumer<IOException> onException) {
        if (!active.get()) {
            return; // We aren't running
        }
        
        if (!additionalFields.isEmpty()) {
            message.additionalFields.putAll(additionalFields);
        }
        
        synchronized (socketLock) {
            ensureConnection();
            
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
                        
                        if (null != onException) {
                            onException.accept(e);
                        }
                    }
                });
            });            
        }
    }
    
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
    }
    
    /**
     * Since the activator and modifier do the same thing, just share this code
     * 
     * @param config the map from activate or modified
     */
    private void osgiSetup(final Map<String, Object> config) {
        consoleMessages.set((Boolean) config.get(GRAYLOG_LOG_CONSOLE));
        
        // See if we have additional fields to pass along
        @SuppressWarnings("unchecked")
        Vector<String> additionalVector = (Vector<String>) config.get(GRAYLOG_ADD_FIELDS);
        if (null != additionalVector && !additionalVector.isEmpty()) {
            additionalFields = additionalVector.stream()
               .map(e -> e.split(":"))
               .filter(a -> a.length == 2)
               .collect(Collectors.toMap(a -> a[0].trim(), a -> a[1].trim()));
        } else {
            additionalFields = Collections.emptyMap();
        }
        
        // Check to see if we have a host, if not the rest doesn't matter
        if (!config.containsKey(GRAYLOG_HOST) || null == config.get(GRAYLOG_HOST)) {
            trace("Cannot start gelf bundle, a host is not configured.");
            active.set(false);
            disconnect();
        } else {
            String newHostname = config.get(GRAYLOG_HOST).toString();
            // There is an odd issue where the configration system either puts in
            // decimals, or a string and a straight cast fails
            Integer newPort = Integer.parseInt(config.get(GRAYLOG_PORT).toString().split("\\.")[0]);
            
            /**
             * If either of these changed, we need to close the current
             * connection.
             */
            if (!(newHostname.equals(hostname) && newPort.equals(port))) {
                hostname = newHostname;
                port = newPort;
                disconnect();
            }
            
            active.set((Boolean) config.get(GRAYLOG_ACTIVE));
            
            if (active.get()) {
                trace("Enabling GELF logging to %s:%d", hostname, port);
                ensureConnection();
            } else {
                disconnect();
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
    }
    
    /**
     * Makes sure we have an active connection
     */
    private void ensureConnection() {
        synchronized (socketLock) {
            if (!transport.isPresent()) {
                try {
                    InetAddress address = InetAddress.getByName(hostname);
                    Socket trans = new Socket(address, port); 
                    trans.setSoTimeout(500);
                    trans.shutdownInput();
                    transport = Optional.of(trans);
                    outputStream = Optional.of(trans.getOutputStream());
                } catch (IOException e) {
                    trace("Failed to connect to %s:%d => %s", hostname, port, e.getMessage());
                    
                    transport = Optional.empty();
                    outputStream = Optional.empty();
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
