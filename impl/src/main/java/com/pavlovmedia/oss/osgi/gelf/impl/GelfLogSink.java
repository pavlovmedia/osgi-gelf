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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;

import com.pavlovmedia.oss.osgi.gelf.impl.external.IronValueHelper;
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
@Component(metatype=true, policy=ConfigurationPolicy.REQUIRE, immediate=true)
@Service(value = LogListener.class)
@Properties({
    @Property(name=GelfLogSink.TRACE_ENABLE, boolValue=false, label="Trace Enable", description="Log messages with unknown levels as debug")
})
public class GelfLogSink implements LogListener {
    static final String TRACE_ENABLE = "graylog.trace.enable";
    
    @Reference
    LogReaderService readerService;
    
    @Reference
    IGelfTransporter gelfServer;
    
    private AtomicBoolean traceOn = new AtomicBoolean(false);
    
    @Activate
    protected void activate(final Map<String, Object> config) {
        traceOn.set(IronValueHelper.getBoolean(config.get(TRACE_ENABLE)));
        readerService.addLogListener(this);
    }
    
    @Modified
    protected void modified(final Map<String,Object> config) {
        traceOn.set(IronValueHelper.getBoolean(config.get(TRACE_ENABLE)));
    }
 
    @Deactivate
    protected void deactivate() {
        readerService.removeLogListener(this);
    }

    public void logged(final LogEntry entry) {
        Optional<GelfMessage> message = GelfMessageConverter.fromOsgiMessage(entry, traceOn);
        message.ifPresent(gelfServer::logGelfMessage);
    }
}
