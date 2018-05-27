interface Moo {
    int moo(int x);
}
public class Foo implements Moo {
    public static void basic_lambda(int x) {
        Object a = Foo::new;
    }
}
