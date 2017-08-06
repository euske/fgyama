public class Foo {
    
    public static int c1(int x) {
	int n = 0;
	for (int i = 0; i < x; i++) {
	    n *= i;
	}
	return n;
    }

    public static int c2(int a, int b) {
	int c = b;
	while (0 < b) {
	    c *= b;
	    b--;
	}
	return c;
    }
    
}
