public class regression_generics {

    static class A<T> {
    }

    public static void test() {
        A<String> a = null;
        foo(a);
    }

    public static void foo(A x) {
    }
}
