public class Foo {
    
    public static int f5(int n) {
	x = (n < 10)? 33 : 44;
	switch (n) {
	case 1:
	case 2:
	    x = 100;
	    break;
	default:
	    x = 200;
	    break;
	}
	return x;
    }
    
}
