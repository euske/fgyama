public class Foo {
    public static void basic_continue(int x) {
	while (true) {
	    if (x == 0) { continue; }
	    x -= 1;
	}
    }
}
