public class basic_fields {

    static class A {
        int x = 0;
        int y;
    }

    public static void fx(A a) {
        a.x = 1;
    }

    public static int fy(A a) {
        fx(a);
        a.y = 2;
        return a.x;
    }

    public static void foo() {
        A a = new A();
        a.x = 3;
        fy(a);
    }
}
