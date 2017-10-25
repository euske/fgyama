public class Foo {
    public static void basic_break(int x) {
	while (true) {
	    if (x == 0) { break; }
	    x -= 1;
	}
	x++;
    }
}
