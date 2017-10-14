public class Foo {
    public static int basic_do(int x) {
	int n = 0;
	do {
	    x /= 2;
	    n++;
	} while (0 < x);
	return n;
    }
}
