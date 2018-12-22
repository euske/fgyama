//  Logger.java
//
package net.tabesugi.fgyama;
import java.io.*;


//  Logger class.
//
public class Logger {

    public static PrintStream out = System.err;

    public static int LogLevel = 1;

    public static void debug(String s) {
	if (2 <= LogLevel) {
	    out.println(s);
	}
    }

    public static void info(String s) {
	if (1 <= LogLevel) {
	    out.println(s);
	}
    }

    public static void error(String s) {
	if (0 <= LogLevel) {
	    out.println(s);
	}
    }
}
