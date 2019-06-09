# FGyama

FGyama, or Flow Graph yama is a dataflow graph extractor for Java.

Prerequisites:
--------------

  * Java/Ant
  * Eclipse JDT (automatically downloaded)
  * Graphviz http://graphviz.org/

How to Build:
-------------

    $ ant get-deps clean build

How to Run:
-----------

    $ ./run.sh net.tabesugi.fgyama.Java2DF Class1.java Class2.java ... > out.xml
    $ python tools/graph2gv.py out.xml > out.gv

Output XML format:
------------------

    <fgyama>
      <file path="HelloWorld.java">
        <graph name="LHelloWorld;.main([Ljava/lang/String;)V" style="static">
          <ast length="84" start="28" type="31"/>
          <scope name="HelloWorld.main">
            <node id="NHelloWorld.main.b0_3" kind="fieldref"
                  type="Ljava/io/PrintStream;" ref="java/lang/System/.out">
              <link src="NHelloWorld.main_4"/>
              <ast length="10" start="73" type="40"/>
            </node>
            <node id="NHelloWorld.main.b0_5" kind="const"
                  type="Ljava/lang/String;" data="Hello World!">
              <ast length="14" start="92" type="45"/>
            </node>
            <node id="NHelloWorld.main.b0_6" kind="call"
                  type="V" data="Ljava/io/PrintStream;.println(Ljava/lang/String;)V">
              <link label="arg0" src="NHelloWorld.main.b0_5"/>
              <link label="obj" src="NHelloWorld.main.b0_3"/>
              <ast length="34" start="73" type="32"/>
            </node>
            ...
          </scope>
        </graph>
      </file>
    </fgyama>

Node types (kinds):
-------------------

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
-------------
    (c-add-style "me"
             '("Java"
               (c-offsets-alist . (
                                   (arglist-cont . c-lineup-argcont)
                                   (arglist-intro . +)
                                   ))
               ))

TODOs:
------
  * getKlass() should be getType().
  * DFMethodType should have an Exception field.
  * Merge DFGraph and DFMethod.
  * Handle consecutive SwitchCases.
  * Lambdas.
  * Method references.
  * Java language spec.: https://docs.oracle.com/javase/specs/
  * Moar unittests.
