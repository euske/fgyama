public class basic_generic {

    static class Moo<T> {

        T x;

        Moo(T x) {
            this.x = x;
        }

        T get() {
            return x;
        }

        Moo<T> copy() {
            return new Moo<T>(x);
        }
    }

    public static void test() {
        Moo<String> moo = new Moo<String>("moo");
        String b = moo.get();
        Moo<String> boo = moo.copy();
    }

    public static <T> T foo(T x) { return x; }
}