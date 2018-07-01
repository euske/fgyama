//  Logger.java
//
package net.tabesugi.fgyama;
import java.io.*;


//  Logger class.
//
public class Logger {

    public static PrintStream out = System.err;

    public static void info(String s) {
        out.println(s);
    }

    public static void error(String s) {
        out.println(s);
    }
}
