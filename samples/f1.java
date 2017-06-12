public class Foo {
    
    public static void f1(int x, int y) {
	int z = x+1;
        z *= y;
	z = moo(z, 2);
	return z;
    }
    
}
