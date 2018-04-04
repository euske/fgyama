//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  UnsupportedSyntax
//
public class UnsupportedSyntax extends Exception {

    static final long serialVersionUID = 1L;

    public ASTNode ast;
    public String name = null;

    public UnsupportedSyntax(ASTNode ast) {
	this.ast = ast;
    }
}
