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

        Moo<T> copy() {
            return new Moo<T>(x);
        }

        class Baa { }
    }

    public static void test() {
        Moo<String> moo = new Moo<String>("moo");
        String b = moo.get().toLowerCase();
        Moo<String> boo = moo.copy();
        Moo<Integer>.Baa mi;
    }

    public static <T,E extends Moo<T>> T foo(E x) { return x.get(); }
}
