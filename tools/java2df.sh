#!/bin/sh
# usage:
#   java2df.sh [opts] *.java
BASEDIR="${0%/*}/.."
TARGETDIR="${BASEDIR}/target"
JVMOPTS="-ea -XX:MaxJavaStackTraceDepth=1000000"
exec java $JVMOPTS -jar "$TARGETDIR"/fgyama-1.0-SNAPSHOT-jar-with-dependencies.jar "$@"
