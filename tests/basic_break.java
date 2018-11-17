public class basic_break {

    public static void foo(int x) {
	while (true) {
	    if (x == 0) { break; }
	    x -= 1;
	}
	x++;
    }
}
