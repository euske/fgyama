class basic_poly_Foo {
    int x = 0;
    public int moo(basic_poly_Foo obj) {
        return obj.x;
    }
}

class basic_poly_Baa extends basic_poly_Foo {
    public int moo(basic_poly_Foo obj) {
        super.moo(obj);         // Foo.moo is called.
        return obj.x+1;
    }
}

public class basic_poly {
    public static void main(String[] args) {
        basic_poly_Foo a = new basic_poly_Foo();
        int x = a.moo(a);       // Foo.moo is called.
        basic_poly_Foo b = new basic_poly_Baa();
        x = b.moo(a);           // Baa.moo is called.
        x = b.moo(b);           // Baa.moo is called.
        basic_poly_Baa c = new basic_poly_Baa();
        x = c.moo(a);           // Baa.moo is called.
        x = c.moo(b);           // Baa.moo is called.
    }
}
