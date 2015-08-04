package com.pavlovmedia.oss.osgi.gelf.lib;

import java.io.IOException;
import java.util.function.Consumer;

public interface IGelfTransporter {
    public void logGelfMessage(GelfMessage message, Consumer<IOException> onException);
    
    public void logGelfMessage(GelfMessage message);
}
