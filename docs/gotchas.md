# Java Language Gotchas

## Two Namespaces

You need two separate namespaces for types (classes) and variables:

    class a {
      a a;
    }

Nuff' said.

## Type Visibility

    import Z;

    // defined(root) = {A, B, C}.
    // visible(root) = {Z, A, B, C}.
    class A {                     // defined(A) = {P}.
      Z z; A a; B b; C c; P p;    // visible(A) = visible(root) + defined(A).
      class P {                   // defined(P) = {X}.
        Z z; A a; B b; C c; P p; X x;  // visible(P) = visible(A) + defined(P).
        class X<Y> {              // defined(X) = {Y}.
          Z z; A a; B b; C c; P p; X x; Y y; // visible(X) = visible(P) + defined(X).
        }
      }
      void foo() {                // defined(foo) = {E}.
        Z z; A a; B b; C c; P p; E e;  // visible(foo) = visible(A) + defined(foo).
        class E { };
      }
    }

    class B extends A {           // defined(B) = defined(A) + {Q,R}.
      Z z; A a; B b; C c; P p; Q q; R r;  // visible(B) = visible(root) + defined(B).
      class Q extends P {         // defined(Q) = defined(P) + {}.
        Z z; A a; B b; C c; P p; Q q; R r; X x;  // visible(Q) = visible(B) + defined(Q).
      }
      class R<T> extends P implements C<T> {  // defined(R) = defined(P) + defined(C) + {T}.
        A a; B b; C c; P p; Q q; R r; S s; T t; X x;  // visible(R) = visible(Q) + defined(R).
      }
    }

    interface C<U> {              // defined(C) = {S,U}.
      Z z; A a; B b; C c; S s; U u;  // visible(C) = visible(root) + defined(C).
      interface S {               // defined(S) = {}.
        Z z; A a; B b; C c; U u; S s;  // visible(S) = visible(C) + defined(S).
      }
    }

## Generic method

    class A {
      <T> void foo(T x) { ... }
      <T> void baa(T x) { ... }
    }

Question: which namespace does `T` belong?

Answer: The two Ts are different types,
so each should belong to different name spaces:
`A/foo/T` and `A/baa/T`.

