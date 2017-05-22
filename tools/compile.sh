#!/bin/sh
. ./.classpath
JDTCP="$JDTDIR/org.eclipse.jdt.core.jar"
JDTCP="$JDTCP:$JDTDIR/org.eclipse.core.runtime.jar"
JDTCP="$JDTCP:$JDTDIR/org.eclipse.equinox.common.jar"
JDTCP="$JDTCP:$JDTDIR/org.eclipse.core.resources.jar"
JDTCP="$JDTCP:$JDTDIR/org.eclipse.core.jobs.jar"
JDTCP="$JDTCP:$JDTDIR/org.eclipse.osgi.jar"
JDTCP="$JDTCP:$JDTDIR/org.eclipse.core.contenttype.jar"
JDTCP="$JDTCP:$JDTDIR/org.eclipse.equinox.preferences.jar"
exec javac -cp "$JDTCP" Java2Xml.java
