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

import com.pavlovmedia.oss.osgi.gelf.lib.GelfMessage;
import com.pavlovmedia.oss.osgi.gelf.lib.IGelfTransporter;

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
@Component()
@Service(value = LogListener.class)
public class GelfLogSink implements LogListener {
    @Property(boolValue=false, label="Active", description="Graylog2 Active")
    final static String GRAYLOG_ACTIVE="graylog.active";
    
    private boolean active;
    
    @Reference
    LogReaderService readerService;
    
    @Reference
    IGelfTransporter gelfServer;
    
    @Activate
    protected void activate(Map<String, Object> config) {
        active = (Boolean) config.get(GRAYLOG_ACTIVE);
        if (active) {
            readerService.addLogListener(this);
        }
    }
    
    @Deactivate
    protected void deactivate() {
        if (active) {
            readerService.removeLogListener(this);
        }
    }

    public void logged(LogEntry entry) {
        if (!active) {
            System.out.println("Logging disabed");
            return; // We aren't running
        }
        
        GelfMessage message = GelfMessageConverter.fromOsgiMessage(entry);
        gelfServer.logGelfMessage(message);
    }
}
