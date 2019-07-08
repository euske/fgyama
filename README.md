# FGyama

FGyama, or Flow Graph yama is a dataflow graph extractor for Java.

## Prerequisites:

  * Java/Ant
  * Eclipse JDT (automatically downloaded)
  * Graphviz http://graphviz.org/

## How to Build:

    $ ant get-deps clean build

## How to Run:

    $ ./run.sh net.tabesugi.fgyama.Java2DF Class1.java Class2.java ... > out.graph
    $ python tools/graph2gv.py out.graph > out.gv

## Output XML format:

    <?xml version="1.0" encoding="UTF-8"?><fgyama>
      <class extends="Ljava/lang/Object;" interface="false" name="LHelloWorld;" path="HelloWorld.java">
        <method name="LHelloWorld;.&lt;clinit&gt;()V" style="initializer">
          <ast end="123" start="0" type="55"/>
          <scope name="LHelloWorld;.&lt;clinit&gt;"/>
        </method>
        <method name="LHelloWorld;.main([Ljava/lang/String;)V" style="static">
          <ast end="121" start="30" type="31"/>
          <scope name="LHelloWorld;.:MethodDeclaration:30:121">
            <scope name="LHelloWorld;.:MethodDeclaration:30:121.:Block:69:121">
              <node id="N654a8762_5" kind="fieldref" ref="@Ljava/lang/System;/.out" type="Ljava/io/PrintStream;">
                <link src="N654a8762_3"/>
                <ast end="89" start="79" type="40"/>
              </node>
              <node data="Hello, World!" id="N654a8762_6" kind="const" type="Ljava/lang/String;">
                <ast end="113" start="98" type="45"/>
              </node>
              <node data="Ljava/io/PrintStream;.println(Ljava/lang/String;)V" id="N654a8762_7" kind="call" type="V">
                <link label="#arg0" src="N654a8762_6"/>
                <link label="#this" src="N654a8762_5"/>
                <ast end="114" start="79" type="32"/>
              </node>
              <node id="N654a8762_8" kind="receive" type="V">
                <link label="#return" src="N654a8762_7"/>
                <ast end="114" start="79" type="32"/>
              </node>
            </scope>
            <node id="N654a8762_1" kind="input" ref="#arg0" type="[Ljava/lang/String;">
              <ast end="67" start="54" type="44"/>
            </node>
            <node id="N654a8762_2" kind="assign" ref="$LHelloWorld;.:MethodDeclaration:30:121/$args" type="[Ljava/lang/String;">
              <link src="N654a8762_1"/>
              <ast end="67" start="54" type="44"/>
            </node>
            <node id="N654a8762_3" kind="input" ref="@Ljava/lang/System;/.out" type="Ljava/io/PrintStream;"/>
            <node id="N654a8762_4" kind="input" ref="#this" type="LHelloWorld;"/>
          </scope>
        </method>
      </class>
    </fgyama>

## Node types (kinds):

| Kind         | Data                |
| ------------ | ------------------- |
| value        | Actual value        |
| valueset     | Value count         |
| op_assign    | Assignment operator |
| op_prefix    | Prefix operator     |
| op_infix     | Infix operator      |
| op_postfix   | Postfix operator    |
| op_typecast  | Casting type        |
| op_typecheck | Checking type       |
| op_iter      |                     |
| ref_var      |                     |
| ref_array    |                     |
| ref_field    |                     |
| assign_var   |                     |
| assign_array |                     |
| assign_field |                     |
| ------------ | ------------------- |
| call         | Method IDs          |
| new          | Method ID           |
| input        |                     |
| output       |                     |
| receive      |                     |
| throw        |                     |
| catch        |                     |
| ------------ | ------------------- |
| join         |                     |
| begin        |                     |
| end          |                     |
| repeat       |                     |
| case         | Label count         |

## Coding style:

    (c-add-style "me"
             '("Java"
               (c-offsets-alist . (
                                   (arglist-cont . c-lineup-argcont)
                                   (arglist-intro . +)
                                   ))
               ))

## TODOs:

  * DFMethodType should have an Exception field.
  * Vararg methods matching.
  * Lambdas.
  * Method references.
  * Handle consecutive SwitchCases.
  * Java language spec.: https://docs.oracle.com/javase/specs/
  * Moar unittests.
