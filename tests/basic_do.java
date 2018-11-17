public class basic_do {

    public static int foo(int x) {
	int n = 0;
	do {
	    x /= 2;
	    n++;
	} while (0 < x);
	return n;
    }
}
