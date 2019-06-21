[![Build Status](http://ci-01.pavlovmedia.net/buildStatus/icon?job=github/pavlovmedia/osgi-gelf/master)](http://ci-01.pavlovmedia.net/job/github/job/pavlovmedia/job/osgi-gelf/job/master/)

OSGi GELF 
=

Builds
------
| Branch | Status | OBR |
|--------|--------|-----|
| Master | [![Build Status](https://travis-ci.org/pavlovmedia/osgi-gelf.svg?branch=master)](https://travis-ci.org/pavlovmedia/osgi-gelf) | |
| 2.0 | [![Build Status](https://travis-ci.org/pavlovmedia/osgi-gelf.svg?branch=2.0-release)](https://travis-ci.org/pavlovmedia/osgi-gelf) | [repository.xml](https://raw.githubusercontent.com/pavlovmedia/osgi-gelf/2.0-release/obr/repository.xml) |

Latest Version
--------------

[ ![Download](https://api.bintray.com/packages/pavlovmedia/pavlov-media-oss/osgi-gelf/images/download.svg) ](https://bintray.com/pavlovmedia/pavlov-media-oss/osgi-gelf/_latestVersion)
[![Maven Status](https://maven-badges.herokuapp.com/maven-central/com.pavlovmedia.oss.osgi.gelf/com.pavlovmedia.oss.osgi.gelf/badge.png)](https://repo1.maven.org/maven2/com/pavlovmedia/oss/osgi/gelf)

==

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


