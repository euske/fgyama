public class Foo {
    public static int basic_xfor(int[] a) {
	int s = 0;
	for (int x : a) {
	    s += x;
	}
	return s;
    }
}
