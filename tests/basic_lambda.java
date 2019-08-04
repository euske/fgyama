import java.util.function.*;

interface Moo {
    abstract String moo(int x);
}

public class basic_lambda {

    Predicate<Integer> isZero = (x -> x == 0);

    public static void foo(int x) {
        Moo a = Integer::toString;
    }
}
