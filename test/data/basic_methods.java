public class Foo {
    public static void fa() {
	int a = 0;
    }
    public static void fb() {
	int b = 0;
	Object a = new Object()
	    {
		public String toString() { return "foo"; }
	    };
	class moo {
	    public int fc() { return 123; }
	}
    }
}
