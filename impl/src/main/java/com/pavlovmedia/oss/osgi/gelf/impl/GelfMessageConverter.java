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

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;

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
        
        if (null != entry.getException()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(baos);
            entry.getException().printStackTrace(writer);
            message.additionalFields.put("exception", baos.toString());
        }
        
        message.additionalFields.put("BundleId", ""+entry.getBundle().getBundleId());
        message.additionalFields.put("BundleSymbolicName", entry.getBundle().getSymbolicName());
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
