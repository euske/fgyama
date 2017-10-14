public class Foo {
    public static int basic_switch(int n) {
	int x;
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
