public class Foo {
    
    public static void f3(int x, int y) {
	int z = 1;
	while (0 < y) {
	    z *= x;
	    y -= 1;
	    if (y == 1) {
		y = 2;
		break;
	    }
	}
	return z;
    }
    
}
