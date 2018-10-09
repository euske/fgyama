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

  * static import.
  * class file generic support.
  * class file enum support.
  * Better handling of GlobalScope.
  * Handle consecutive SwitchCases.
  * Lambdas.
  * Method references.
