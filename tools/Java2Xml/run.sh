#!/bin/sh
JDTDIR=../../lib
exec java -cp build:$JDTDIR/org.eclipse.core.contenttype.jar:$JDTDIR/org.eclipse.core.jobs.jar:$JDTDIR/org.eclipse.core.resources.jar:$JDTDIR/org.eclipse.core.runtime.jar:$JDTDIR/org.eclipse.equinox.common.jar:$JDTDIR/org.eclipse.equinox.preferences.jar:$JDTDIR/org.eclipse.jdt.core.jar:$JDTDIR/org.eclipse.osgi.jar Java2Xml "$@"
