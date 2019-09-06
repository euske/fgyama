# Interprocedural Dataflow Analysis

 - Wrap each *intra* procedural Node as an *inter* procedural Vertex.
   (Let's use a different word to distinguish intra/inter procedural use.)

 - Each Vertex is connected with Edges.
   (Again, let's use a different word from interprocedural *links*.)

 - Node and Vertex are one-to-one mapped, while Links and Edges are generally not.
   (In general, there are more Edges than Links.)

 - The reason that there are more Edges is that each Input/Output nodes (vertices)
   in a method can be connected to the Input/Output nodes in multiple methods.
   (caller/callee).
