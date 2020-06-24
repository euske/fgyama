# Java Language Gotchas

## Two Namespaces

You need two separate namespaces for types (classes) and variables:

class a {
  a a;
}

Nuff' said.

## Type Visibility

class A {
  // Visible: A, B, C, P.
  class P {
    // Visible: A, B, C, P, X.
    class X<Y> {
      // Visible: A, B, C, P, X, Y.
    }
  }
}

class B extends A {
  // Visible: A, B, C, P, Q, R.
  class Q extends P {
    // Visible: A, B, C, P, Q, R, X.
  }
  class R<T> extends P implements C<T> {
    // Visible: A, B, C, P, Q, R, S, T, X.
  }
}

interface C<Z> {
  // Visible: A, B, C, S, Z.
  interface S {
    // Visible: A, B, C, S, Z.
  }
}

## Generic method

class A {
  <T> void foo(T x) { ... }
  <T> void baa(T x) { ... }
}

Question: which namespace does T belong?

Answer: The two Ts are different types,
so each should belong to different name spaces:
A/foo/T and A/baa/T.

