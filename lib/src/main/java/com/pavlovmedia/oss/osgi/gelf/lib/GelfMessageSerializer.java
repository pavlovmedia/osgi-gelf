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
package com.pavlovmedia.oss.osgi.gelf.lib;

import java.io.IOException;
import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * A serializer module for Jackson that will turn our GelfMessage
 * into JSON.
 * 
 * @author Shawn Dempsay
 *
 */
public class GelfMessageSerializer extends JsonSerializer<GelfMessage> {

    @Override
    public void serialize(final GelfMessage value, final JsonGenerator jgen,
            final SerializerProvider provider) throws IOException,
            JsonProcessingException {
        jgen.writeStartObject();
        jgen.writeStringField("version", value.version);
        jgen.writeStringField("host", value.host);
        jgen.writeStringField("short_message", value.short_message);
        jgen.writeStringField("full_message", value.full_message);
        
        BigDecimal bd = new BigDecimal(value.timestamp);
        bd = bd.divide(new BigDecimal(1000), 4, BigDecimal.ROUND_DOWN);
        jgen.writeNumberField("timestamp", bd);
        jgen.writeNumberField("level", value.level);
        for (String key : value.additionalFields.keySet()) {
            jgen.writeStringField("_"+key, value.additionalFields.get(key));
        }
        jgen.writeEndObject();
    }

}
