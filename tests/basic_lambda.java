import java.util.function.*;

interface Moo {
    abstract String moo(int x);
}

public class basic_lambda {

    Predicate<Integer> isZero = (i -> i == 0);

    public static void main(String[] args) {
        Moo a = Integer::toString;
        String y = "abc";
        bar(a);
        bar(z -> y+z);
    }

    public static void bar(Moo m) {
        System.out.println(m.moo(123));
    }
}
