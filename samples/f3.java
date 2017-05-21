public class Foo {
    
    public static void f3(int x) {
	int y = 1;
	while (0 < x) {
	    y *= x;
	    x -= 1;
	}
	return y;
    }
    
}
