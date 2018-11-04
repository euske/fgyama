import java.util.EnumSet;

public class regression_enumset {

    enum A { FOO }

    // This causes an infinite recursion.
    EnumSet<A> x = EnumSet.of(A.FOO);
}
