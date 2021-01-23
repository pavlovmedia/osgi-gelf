package com.pavlovmedia.oss.osgi.gelf.lib;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * 
 * @author shawn
 *
 */
public interface IGelfTransporter {
    void setLoggedAsHostname(String hostname);
    
    void logGelfMessage(GelfMessage message, Consumer<IOException> onException);
    
    void logGelfMessage(GelfMessage message);
}
