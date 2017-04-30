public class Foo {
    
    public static void foo(int x, int y) {
	int z = x+1;
	if (z < y) {
	    int a = z;
	    x += a;
	}
	return y*z;
    }
    
}
