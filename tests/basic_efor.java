public class basic_xfor {

    public static int foo(int[] a) {
	int s = 0;
	for (int x : a) {
	    s += x;
	}
	return s;
    }
}
