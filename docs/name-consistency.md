# Measuring Naming Consistency

## What is Consistency?

Intuition:
Let X be a set of features and Y be the feature that is correlated with X.

(Caution:
Let's not use a term "variable" here because it mix up with programming terms!)

When the prediction X → Y has a high confidence, an occurance of
X → Z (or lack of Y) suggests that there's an inconsistency;
Z should be corrected as Y.

## Mathematical Formulation

When P(Y | X) is high and P(!Y | X) is low, there's a strong
colleration that X → Y.

Find a set of features X such that it maximises P(Y | X) / P(!Y | X).
