OSGi GELF 
=

This project is intended to be a facility to bridge the gap between OSGi spec logging and the GELF logging protocol used by GrayLog2 (http://www.graylog2.org/).

Simple usage
-
Getting off the ground is not hard if you have the following:

* A GrayLog2 server that is running a TCP GELF input
* An OSGi runtime that has the following bundles:
 * OSGi Logging bundle (see Apache Felix Log Bundle)  
 * OSGi Configuration Admin Service bundle (see Apache Felix Configuration Admin Service) 
 * The Apache Declarative Services Bundle
 * The GELF Sink Mega module (from this project)

Onc you have this running you just need to configure the GrayLog server, and enable logging. Once this is on, any logs written to the OSGi log will also be written to GrayLog

Sample
-

I will place a full walkthough on my blog http://shawnsrandom.blogspot.com/ in the very near future that will tie this all up in a nice bow.


