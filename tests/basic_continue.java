public class basic_continue {

    public static void foo(int x) {
	while (true) {
	    if (x != 0) { continue; }
	    x -= 1;
	}
        x++;
    }
}
