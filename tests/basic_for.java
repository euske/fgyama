public class basic_for {

    public static int foo(int n) {
	int x = 0;
	for (int i = 0; i < n; i += 1) {
	    x += i;
	}
	return x;
    }
}
