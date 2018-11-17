enum E {
    Moo(1),
    Baa(2);
    private int x;
    private E(int x) {
        this.x = x;
    }
}

public class basic_enum {
    public static void foo() {
        E x = E.Moo;
        switch (x) {
        case Moo:
            break;
        case Baa:
            break;
        }
    }
}
