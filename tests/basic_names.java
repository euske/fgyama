package dom.meep;

class basic_names {

    class Bar {}
    int y = 123;

    public void doit() {
        System.out.println("Foo!");
        class Baz {
            int x;
            void moo() { }
        }
        Baz baz = new Baz();
        baz.x = this.y;
        baz.moo();
    }

    public void meh() {
        m33p(); // unresolved.
    }

    public static void main(String[] args) {
        basic_names foo = new basic_names();
        foo.doit();
    }
}
