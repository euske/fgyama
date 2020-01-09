public class basic_generics {

    static class Moo<T> {

        T x;

        Moo(T x) {
            this.x = x;
        }

        T get() {
            T y = x;
            return y;
        }

        <S> S get2(Moo<S> x) {
            return x.get();
        }

        Moo<T> copy() {
            return new Moo<T>(x);
        }

        class Baa { }
    }

    public static void test() {
        Moo<String> moo = new Moo<String>("moo");
        String b = moo.get().toLowerCase();
        String c = moo.get2(moo).toLowerCase();
        Moo<String> boo = moo.copy();
        Moo<Integer>.Baa mi;
    }

    public static <T,E extends Moo<T>> T foo(E x) { return x.get(); }
}
