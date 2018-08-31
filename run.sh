#!/bin/sh
# usage:
#   run.sh net.tabesugi.fgyama.Java2DF *.java
#   run.sh CommentExtractor *.java
if [ $# -eq 0 ]; then
    echo "usage: $0 package.class [args]"
    exit 1
fi
BASEDIR=${0%/*}
LIBDIR=${BASEDIR}/lib
CLASSPATH=${BASEDIR}/target
CLASSPATH=${CLASSPATH}:${LIBDIR}/bcel-6.2.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/junit-4.12.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/xmlunit-1.6.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.jdt.core-3.12.3.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.core.resources-3.11.1.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.core.expressions-3.5.100.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.core.runtime-3.12.0.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.osgi-3.11.3.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.equinox.common-3.8.0.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.core.jobs-3.8.0.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.equinox.registry-3.6.100.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.equinox.preferences-3.6.1.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.core.contenttype-3.5.100.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.equinox.app-1.3.400.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.core.filesystem-1.6.1.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.text-3.6.0.jar
CLASSPATH=${CLASSPATH}:${LIBDIR}/org.eclipse.core.commands-3.8.1.jar
exec java -cp $CLASSPATH "$@"
