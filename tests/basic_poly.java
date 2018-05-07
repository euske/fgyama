class Foo {
    int x = 0;
    public int moo(Foo obj) {
        return obj.x;
    }
}

class Baa extends Foo {
    public int moo(Foo obj) {
        super.moo(obj);
        return obj.x+1;
    }

    public static void main() {
        Foo a = new Foo();
        int x = a.moo(a);
        Foo b = new Baa();
        x = b.moo(a);
        x = b.moo(b);
        Baa c = new Baa();
        x = c.moo(a);
        x = c.moo(b);
    }
}
