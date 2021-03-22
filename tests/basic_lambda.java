import java.util.function.*;

interface Moo {
    abstract String moo(int x);
}
interface Foo {
    abstract int foo(int x, int y);
}

public class basic_lambda {

    Predicate<Integer> isZero = (i -> i == 0);

    public static void main(String[] args) {
        Moo a = Integer::toString;
        String y = "abc";
        bar(a);
        bar((b,c) -> b+c);
    }

    public static void bar(Moo m) {
        System.out.println(m.moo(123));
    }
    public static void bar(Foo f) {
        System.out.println(f.foo(2, 3));
    }
}
