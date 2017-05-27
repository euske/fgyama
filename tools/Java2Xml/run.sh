#!/bin/sh
exec java -cp build:lib/org.eclipse.core.contenttype.jar:lib/org.eclipse.core.jobs.jar:lib/org.eclipse.core.resources.jar:lib/org.eclipse.core.runtime.jar:lib/org.eclipse.equinox.common.jar:lib/org.eclipse.equinox.preferences.jar:lib/org.eclipse.jdt.core.jar:lib/org.eclipse.osgi.jar Java2Xml "$@"
