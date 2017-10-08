public class Foo {
    public static void f6a() {
	int a = 0;
    }
    public static void f6b() {
	int b = 0;
	Object a = new Object()
	    {
		public String toString() { return "foo"; }
	    };
	class moo {
	    public int f6c() { return 123; }
	}
    }
}
