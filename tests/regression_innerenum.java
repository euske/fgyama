// This causes infinite recursion.

public class regression_innerenum<T extends Enum<T>> {

    class C<T extends Enum<T>> {
        regression_innerenum<T> x;
    }

}
