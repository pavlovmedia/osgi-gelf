# Set this to where the tool jar is
OBR_TOOL_JAR=~/buildspace/core/java/maven-obr-plugins/tool/target/dhc.maven.plugins.obr.tool-2.1.0-SNAPSHOT.jar
java -jar ${OBR_TOOL_JAR} https://dl.bintray.com/pavlovmedia/pavlov-media-oss/com/pavlovmedia/oss/osgi/gelf/com.pavlovmedia.oss.osgi.gelf.impl/ https://dl.bintray.com/pavlovmedia/pavlov-media-oss/com/pavlovmedia/oss/osgi/gelf/com.pavlovmedia.oss.osgi.gelf.lib/
