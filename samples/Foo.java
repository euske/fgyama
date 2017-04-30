public class Foo {
    
    public static void foo(int x, int y) {
	int z = x+1;
	if (z < y) {
	    x += z;
	}
	return y*z;
    }
    
}
