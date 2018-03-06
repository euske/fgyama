package dom.meep;

class Foo {

    class Bar {}
    int y = 123;

    public void doit() {
        System.out.println("Foo!");
        class Baz {
            int x;
        }
        Baz baz = new Baz();
        baz.x = this.y;
    }

    public void meh() {
        m33p(); // unresolved.
    }

    public static void main(String[] args) {
        Foo foo = new Foo();
        foo.doit();
    }
}
