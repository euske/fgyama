# FGyama Documentation

(incomplete)

## Motivation

 - Data Flow Graph is a better way to analyze program semantics.
 - Source code analysis was preferred.

## Blurb

 - Produces interprocedural data flow graphs entirely from source code.
 - Can analyze programs without complete dependencies (jar files).
 - Supports up to most JDK 8 language features. (Method references under working).
 - Detailed output with type information, call graphs and source text references.
 - Small external dependency (only Java syntax parser is needed).
 - Comes with Python tools to analyze/manipulate/store graphs.

## History

 - Apr. 2017: originally started as a Python script using SrcML (https://www.srcml.org/)
 - May. 2017: switch to Java/JDT.
 - Apr. 2018: going interprocedural.
 - Mar. 2019: basic features supported.
 - Aug. 2019: Lambda implementation.
