# FGyama Documentation

(incomplete)

## Motivation

 - Dataflow Graph is a better way to analyze program semantics.
 - Source code analysis was preferred.
 - Needs to process a large program.

## Blurb

 - Produces interprocedural dataflow graphs entirely from source code.
 - Can analyze programs without complete dependencies (jar files).
 - Able to process a large codebase (~100kloc) in a few minutes.
 - Supports up to most JDK 8 language features. (Method references under working).
 - Detailed output with type information, call graphs and source text references.
 - Small external dependency - JDT for syntax parser and BCEL for byte code parser.
 - Comes with Python tools to analyze/manipulate/store graphs.

## History

 - Apr. 2017: Originally started as a Python script using SrcML (https://www.srcml.org/)
 - May. 2017: Switch to Java/JDT. (https://www.eclipse.org/jdt/)
 - Apr. 2018: Going interprocedural.
 - Mar. 2019: Basic features supported.
 - Aug. 2019: Lambda implementation.

## Design Decisions

 - Should work as a Unix-y command without a lot of configuration.
 - Spit as much information as possible in a machine readable format (XML) and
   leave the postprocessing to a later process (Python).
 - Try to stick to a standard programming constructs in Java (variables, classes
   and methods) but keep the overall framework open to any language.
 - Treat function returns and exceptions in the same way.
   Exceptions can be regarded as a value return in a special channel.
 - The main program (Java2DF) only takes care of *intra*-procedural dataflow
   with interprocedural references. A later program takes this
   information and builds an interprocedural graph (`interproc.py`).

## Processing Stages

 1. Parse the source code and construct hierachical namespaces for
    types and variables.
 2. Process imports and resolve module paths.
 3. Load each class and list its fields and methods.
 4. Resolve type and variable references. Create interprocedural links.
 5. Process each method and procude a graph.

## Important Concepts

<dl>
<dt> Type Space (<code>DFTypeSpace</code>)
<dd> A hierarchical namespace where Java classes reside.

<dt> Variable Scope (<code>DFVarScope</code>)
<dd> A hierarchical namespace where Java variables reside.
     This is separated form a type space in Java, but the hierarchy is
     intertwined because of inline classes or lambdas.

<dt> Type (<code>DFType</code>) and Class (<code>DFKlass</code>)
<dd> Corresponds to Java types and Java classes.
     (<code>DFKlass</code> is a subclass of <code>DFType</code>).

<dt> Ref (<code>DFRef</code>)
<dd> Variables, class fields or array locations.
     It refers to a place where a Java value can be stored or passed.
     Every Ref belongs to exactly one Scope.
     Note that FGyama has only field-sensitivity but not instance-
     sensitivity, so the same field at a different instance is <strong>not</strong>
     distinguished. Similarly, all array values are assimilated.

<dt> Frame (<code>DFFrame</code>)
<dd> A Frame is a portion of code where a value can be directly passed
     to the outer part of code. For example, a method is naturally a Frame
     because a value can reach the end with <code>return</code>,
     skipping the remaining statements. Similarly, a <code>try ... catch</code>
     statement also creates a Frame that allows value exiting
     (with <code>throw</code>). Also any loop creates a Frame as one can
     pass a value directly to the outside with a <code>break</code> statement.
     Usually a code block can mean that there is a corresponding Frame, but
     not always. (For example, <code>if ... else</code> doesn't create one
     because it's not breakable.)

<dt> Exit (<code>DFExit</code>)
<dd> An Exit is a pair of an exiting value and its target Frame.
     An Exit is created for control flow statements such as
     <code>break</code>, <code>throw</code>, and <code>return</code>.
     When the target Frame is further outside the current Frame,
     the Exit is relayed to the outer Frame (<em>Exit chaining</em>).

<dt> Node (<code>DFNode</code>)
<dd> A Node represents a value in Java in the dataflow graph.
     A Node is usually tied to a Java type and Ref (optionally).
     A Node has zero or more connections (<em>Links</em>) to other Nodes.
     It can be also associated with a portion of the original source code.

<dt> Context (<code>DFContext</code>)
<dd> A Context holds the latest values (Nodes) of each variable
     that is currently visible in the scope, as well as a value being
     computed at the right-hand side of an expression.

<dt> Join Node (<code>JoinNode</code>)
<dd> A mechanism to handle conditional execution (<code>if</code>) in a
     dataflow graph. Both true and false graphs are generated and each
     output at both cases are <em>joined</em> with a Join Node.
     A Join Node works as a ternary operator that takes three values:
     <code>true</code>, <code>false</code> and <code>cond</code>.

</dl>
