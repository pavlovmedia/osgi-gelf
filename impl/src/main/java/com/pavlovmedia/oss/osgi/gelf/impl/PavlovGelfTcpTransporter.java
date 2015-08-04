package com.pavlovmedia.oss.osgi.gelf.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pavlovmedia.oss.osgi.gelf.lib.GelfMessage;
import com.pavlovmedia.oss.osgi.gelf.lib.IGelfTransporter;

@Component(metatype=true)
@Service(value=IGelfTransporter.class)
public class PavlovGelfTcpTransporter implements IGelfTransporter {
    @Property(boolValue=false, label="Active", description="Graylog2 Active")
    final static String GRAYLOG_ACTIVE="graylog.active";
    
    @Property(label="Host", description="Graylog2 Target Host")
    final static String GRAYLOG_HOST="graylog.host";

    @Property(intValue=12201, label="Port", description="Graylog2 Port")
    final static String GRAYLOG_PORT="graylog.port";

    @Property(boolValue=false, label="Console Messages", description="Log messages to the console")
    final static String GRAYLOG_LOG_CONSOLE = "graylog.console";
    
    private final ObjectMapper  mapper       = new ObjectMapper();
    
    private boolean active;
    private String hostname;
    private int port;
    private boolean consoleMessages;
    
    private Socket transport = null;
    private OutputStream outputStream = null;
    
    @Activate
    protected void activate(Map<String, Object> config) {
        consoleMessages =  (Boolean) config.get(GRAYLOG_LOG_CONSOLE);
        
        // Check to see if we have a host, if not the rest doesn't matter
        if (!config.containsKey(GRAYLOG_HOST) || null == config.get(GRAYLOG_HOST)) {
            String message = "Cannot start gelf bundle, a host is not configured.";
            if (consoleMessages) {
                System.err.println(message);
            }
            active = false;
            return;
        } else {
            hostname = config.get(GRAYLOG_HOST).toString();
        }
            
        active = (Boolean) config.get(GRAYLOG_ACTIVE);
        port = (Integer) config.get(GRAYLOG_PORT);
        
        
        if (active) {
            if (consoleMessages) {
                String message = String.format("Enabling GELF logging to %s:%d", hostname, port);
                System.out.println(message);
            }
            ensureConnection();
        }
    }
    
    private synchronized void ensureConnection() {
        if (null == transport) {
            try {
                InetAddress address = InetAddress.getByName(hostname); 
                transport = new Socket(address, port);
                transport.setSoTimeout(500);
                transport.shutdownInput();
                outputStream = transport.getOutputStream();
            } catch (IOException e) {
                String message = String.format("Failed to connect to %s:%d => %s", hostname, port, e.getMessage());
                if (consoleMessages) {
                    System.err.println(message);
                    e.printStackTrace();
                }
                transport = null;
                outputStream = null;
            }
        }
    }
    
    @Deactivate
    protected void deactivate() {
        if (null != transport) {
            String message = "Shutting down GELF logging";
            if (consoleMessages) {
                System.out.println(message);
            }
            try {
                transport.close();
            } catch (IOException e) { /* Do nothing */ }
            transport = null;
            outputStream = null;
        }
    }
    
    @Override
    public void logGelfMessage(GelfMessage message, Consumer<IOException> onException) {
        if (!active) {
            System.out.println("Logging disabed");
            return; // We aren't running
        }
        
        ensureConnection();
        
        if (null == transport) {
            System.out.println("Transport not up");
            return; // Not getting connected/reconnected
        }
        
        try {
            byte[] messageBytes = mapper.writeValueAsBytes(message);
            outputStream.write(messageBytes);
            // There is a bug in GELF that requires us to end with a null byte
            outputStream.write(new byte[] { '\0' });
        } catch (IOException e) {
            if (null != onException) {
                onException.accept(e);
            }
        }
    }

    @Override
    public void logGelfMessage(GelfMessage message) {
        logGelfMessage(message, (e) -> { System.err.println("Failed to serialize message: "+e.getMessage()); });
    }

}
