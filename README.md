# FGyama

FGyama, or Flow Graph yama is a dataflow extractor for program analysis.

Prerequisites:

  * Java/Ant
  * Eclipse JDT (automatically downloaded)
  * Graphviz http://graphviz.org/

How to Build:

    $ ant get-deps
    $ ant graph -Dinput=samples/f1.java -Dout_svg=out.svg

Node types:

  * const
  * valueset
  * ref
  * arrayref
  * fieldref
  * assign
  * arrayassign
  * fieldassign
  * assignop
  * prefix
  * infix
  * postfix
  * typecast
  * instanceof
  * iter

  * input
  * output
  * call
  * new
  * exception

  * join
  * begin
  * end
  * repeat
  * case

TODOs:

import java.util.List;
public class G {
    private String b[];
    public G(List<String> a) {
        this.b = a.toArray(new String[1]);
        int x = this.b.length;
    }
}

  * Interprocedural field watch.
  * static import.
  * class file enum support.
  * Better handling of GlobalScope.
  * Handle consecutive SwitchCases.
  * Lambdas.
  * Method references.
