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

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Class representation of a GELF message. Used with the GelfMessageSerializer
 * to get the JSON needed for a GELF packet
 * @author Shawn Dempsay
 *
 */
@JsonSerialize(using=GelfMessageSerializer.class)
public class GelfMessage {
	public final String version = "1.1";
	public String host;
	public String short_message;
	public String full_message;
	
	/** Milliseconds since UNIX epoch */
	public long timestamp;
	public int level;
	public Map<String,String> additionalFields = new HashMap<>();
	
}
