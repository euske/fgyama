//  Logger.java
//
package net.tabesugi.fgyama;
import java.io.*;


//  Logger class.
//
public class Logger {

    public static PrintStream out = System.err;

    public static int LogLevel = 0;

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
