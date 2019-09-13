public class basic_switch {

    public static int foo(int n) {
	int x = 0;
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
