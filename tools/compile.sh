#!/bin/sh
. ./.classpath
exec javac -cp "$JDTCP" JDTParser.java
