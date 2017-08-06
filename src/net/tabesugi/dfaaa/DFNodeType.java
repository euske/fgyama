//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFNodeType
//
public enum DFNodeType {
    None,
    Refer,
    Const,
    Operator,
    Assign,
    Branch,
    Join,
    Loop,
    Terminal,
}
