# FGyama

FGyama, or Flow Graph yama is a dataflow extractor for program analysis.

Prerequisites:

  * Java/Ant
  * Eclipse JDT (automatically downloaded)
  * Graphviz http://graphviz.org/

How to Build/Run:

    $ ant get-deps
    $ ant graph -Dinput=tests/basic_assignd.java

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
  * throw

  * join
  * begin
  * end
  * repeat
  * case

Coding style:
  (c-add-style "me"
             '("Java"
               (c-offsets-alist . (
				   (arglist-cont . c-lineup-argcont)
				   (arglist-intro . +)
                                   ))
               ))

TODOs:

  * Interprocedural field watch.
  * Handle consecutive SwitchCases.
  * Lambdas.
  * Method references.
  * Moar unittests.
