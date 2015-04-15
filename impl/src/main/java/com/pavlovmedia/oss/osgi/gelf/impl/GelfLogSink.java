/*
 * Copyright 2014 Pavlov Media
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pavlovmedia.oss.osgi.gelf.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Map;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.LogService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pavlovmedia.oss.osgi.gelf.lib.GelfMessage;

/**
 * Implementation that subscribes to the Log Reader interface
 * and will register and unregister this target.
 * 
 * Connection options are passed in using the configuration manager.
 * Note, this is TCP GELF only.
 * 
 * @author Shawn Dempsay
 *
 */
@Component(metatype = true)
@Service(value = LogListener.class)
public class GelfLogSink implements LogListener {
    private final ObjectMapper  mapper       = new ObjectMapper();

    @Property(boolValue=false, label="Active", description="Graylog2 Active")
    private final static String GRAYLOG_ACTIVE="graylog.active";
    
    @Property(label="Host", description="Graylog2 Target Host")
    private final static String GRAYLOG_HOST="graylog.host";

    @Property(intValue=12201, label="Port", description="Graylog2 Port")
    private final static String GRAYLOG_PORT="graylog.port";

    @Property(boolValue=false, label="Console Messages", description="Log messages to the console")
    private final static String GRAYLOG_LOG_CONSOLE = "graylog.console";
    
    private boolean active;
    private String hostname;
    private int port;
    private boolean consoleMessages;
    
    private Socket transport = null;
    private OutputStream outputStream = null;

    @Reference
    LogReaderService readerService;
    
    @Reference
    LogService logger;
    
    @Activate
    protected void activate(Map<String, Object> config) {
        consoleMessages =  (Boolean) config.get(GRAYLOG_LOG_CONSOLE);
        
        // Check to see if we have a host, if not the rest doesn't matter
        if (!config.containsKey(GRAYLOG_HOST) || null == config.get(GRAYLOG_HOST)) {
            String message = "Cannot start gelf bundle, a host is not configured.";
            logger.log(LogService.LOG_INFO, message);
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
                logger.log(LogService.LOG_INFO, message);
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
                transport.shutdownInput();
		outputStream = transport.getOutputStream();
                System.out.println("Connecting to the log service "+readerService);
                readerService.addLogListener(this);
            } catch (IOException e) {
                String message = String.format("Failed to connect to %s:%d => %s", hostname, port, e.getMessage());
                logger.log(LogService.LOG_ERROR, message, e);
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
            logger.log(LogService.LOG_INFO, message);
            if (consoleMessages) {
                System.out.println(message);
            }
            try {
                readerService.removeLogListener(this);
                transport.close();
            } catch (IOException e) { /* Do nothing */ }
            transport = null;
	    outputStream = null;
        }
    }

    public void logged(LogEntry entry) {
        if (!active) {
            System.out.println("Logging disabed");
            return; // We aren't running
        }
        
        ensureConnection();
        
        if (null == transport) {
            System.out.println("Transport not up");
            return; // Not getting connected/reconnected
        }
        
        GelfMessage message = GelfMessageConverter.fromOsgiMessage(entry);
        try {
            System.out.println("Sending log message :"+mapper.writeValueAsString(message));
            byte[] messageBytes = mapper.writeValueAsBytes(message);
            outputStream.write(messageBytes);
            // There is a bug in GELF that requires us to end with a null byte
            outputStream.write(new byte[] { '\0' });
        } catch (JsonProcessingException e) {
            String emessage = "Failed serializing a GelfMessage " + e.getMessage();
            logger.log(LogService.LOG_ERROR, emessage, e);
            if (consoleMessages) {
                System.err.println(emessage);
                e.printStackTrace();
            }
        } catch (IOException e) {
            String emessage = "Failed serializing a GelfMessage " + e.getMessage();
            logger.log(LogService.LOG_ERROR, emessage, e);
            if (consoleMessages) {
                System.err.println(emessage);
                e.printStackTrace();
            }
            
            try {
                transport.close();
            } catch (IOException e1) { /* Do nothing */ }
            transport = null;
	    outputStream = null;
        }
    }
}
