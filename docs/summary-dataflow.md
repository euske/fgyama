# Dataflow Summarization

Quick way to present the overall "flow" of a large program.

## Methodology

 1. Obtain an interprocedural dataflow graph (lattice).
 2. Find strongly connected components (loops) and lump each of them as one node.
 3. Perform main path analysis. (There are multiple ways.)
 4. Present the resultant graph as a summary.
