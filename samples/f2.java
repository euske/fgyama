public class Foo {
    
    public static void f2(int x, int y) {
	int z = x+1;
	if (z < y) {
	    int a = z;
	    x += a;
	}
	return x*z;
    }
    
}
