class basic_poly_Foo {
    int x = 0;
    public int moo(basic_poly_Foo obj) {
        return obj.x;
    }
}

class basic_poly_Baa extends basic_poly_Foo {
    public int moo(basic_poly_Foo obj) {
        super.moo(obj);
        return obj.x+1;
    }

    public static void main() {
        basic_poly_Foo a = new basic_poly_Foo();
        int x = a.moo(a);
        basic_poly_Foo b = new basic_poly_Baa();
        x = b.moo(a);
        x = b.moo(b);
        basic_poly_Baa c = new basic_poly_Baa();
        x = c.moo(a);
        x = c.moo(b);
    }
}
