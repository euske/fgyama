public class regression_deepreify {

    class A<X> {
        B<A<X>> b;
        C<A<X>> c;
        D<A<X>> d;
        E<A<X>> e;
        F<A<X>> f;
        G<A<X>> g;
    }
    class B<X> {
        A<B<X>> a;
        C<B<X>> c;
        D<B<X>> d;
        E<B<X>> e;
        F<B<X>> f;
        G<B<X>> g;
    }
    class C<X> {
        A<C<X>> a;
        B<C<X>> b;
        D<C<X>> d;
        E<C<X>> e;
        F<C<X>> f;
        G<C<X>> g;
    }
    class D<X> {
        A<D<X>> a;
        B<D<X>> b;
        C<D<X>> c;
        E<D<X>> e;
        F<D<X>> f;
        G<D<X>> g;
    }
    class E<X> {
        A<E<X>> a;
        B<E<X>> b;
        C<E<X>> c;
        D<E<X>> d;
        F<E<X>> f;
        G<E<X>> g;
    }
    class F<X> {
        A<F<X>> a;
        B<F<X>> b;
        C<F<X>> c;
        D<F<X>> d;
        E<F<X>> e;
        G<F<X>> g;
    }
    class G<X> {
        A<G<X>> a;
        B<G<X>> b;
        C<G<X>> c;
        D<G<X>> d;
        E<G<X>> e;
        F<G<X>> f;
    }

}
