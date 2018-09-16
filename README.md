# FGyama

FGyama, or Flow Graph yama is a dataflow extractor for program analysis.

Prerequisites:

  * Java/Ant
  * Eclipse JDT (automatically downloaded)
  * Graphviz http://graphviz.org/

How to Build:

    $ ant get-deps
    $ ant graph -Dinput=samples/f1.java -Dout_svg=out.svg

TODOs:

  * DFType should be lookup up from DFTypeSpace (not DFTypeFinder).
  * Treat each parametered type differently.
  * Better handling of GlobalScope.
  * Handle consecutive SwitchCases.
  * Lambdas.
  * Method references.
