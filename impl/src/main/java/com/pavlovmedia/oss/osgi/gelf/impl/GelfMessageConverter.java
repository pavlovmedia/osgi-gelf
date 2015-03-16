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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Dictionary;

import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogService;

import com.pavlovmedia.oss.osgi.gelf.lib.GelfMessage;

/**
 * This is a utility class that will convert an OSGi LogEntry into
 * a suitably formatted Gelf Message
 * 
 * @author Shawn Dempsay
 *
 */
public class GelfMessageConverter {
    
    private static String _hostname = null;
    
    /**
     * This method will determine the hostname and then cache it
     * for future use.
     * @return the hostname of the system
     */
    public static String getHostname() {
        if (null == _hostname) {
            try {
                _hostname = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                System.err.println("Failed to find hostname "+e.getMessage());
                _hostname = "Unknown";
            }
        }
        return _hostname;
    }
    
    /**
     * Takes an OSGi LogEntry and converts it into a GelfMessage
     * This will convert over all the common things and then add
     * on some additional information into additional fields.
     * @param entry The OSGi LogEntry
     * @return A GelfMessage object that represents the same data
     */
    public static GelfMessage fromOsgiMessage(LogEntry entry) {
        GelfMessage message = new GelfMessage();
        message.host = getHostname();
        message.short_message = entry.getMessage();
        message.full_message = entry.getMessage();
        message.timestamp = entry.getTime();
        message.level = gelfLevelFromOsgiLevel(entry.getLevel());
        
        // If we have an exception, we just replace full_message
        // Graylog will reformat it to replace newlines with 
        // html breaks for correct displays.
        if (null != entry.getException()) {
            try ( StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw, true); ) {
                entry.getException().printStackTrace(pw);
                message.full_message = sw.toString();    
            } catch (IOException e1) {
                // These will come close,so there really
                // isn't anything to do with them.
            }
        }
        
        message.additionalFields.put("Bundle-Id", ""+entry.getBundle().getBundleId());
        message.additionalFields.put("Bundle-SymbolicName", entry.getBundle().getSymbolicName());
        
        @SuppressWarnings("rawtypes")
        Dictionary d = entry.getBundle().getHeaders();
        
        message.additionalFields.put("Bundle-Version", d.get("Bundle-Version").toString());
        message.additionalFields.put("Bundle-Name", d.get("Bundle-Name").toString());
        
        return message;
    }
    
    /**
     * Converts the OSGi error level into the GELF error level
     * @param osgiLevel an OSGi LegService level
     * @return The matching GELF log level
     */
    public static int gelfLevelFromOsgiLevel(int osgiLevel) {
        switch (osgiLevel) {
        case LogService.LOG_DEBUG:
            return 0;
        case LogService.LOG_INFO:
            return 1;
        case LogService.LOG_WARNING:
            return 2;
        default:
            return 3; // Error for anything we don't understand
        }
    }
}
