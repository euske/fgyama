#!/bin/sh
. ./.classpath
exec java -cp .:"$JDTCP" JDTParser "$@"
