public class Foo {
    
    public static void f1(int x, int y) {
	int z = x+1;
        z *= y;
	z = moo(z, 2);
	int[] a = new int[10] { 2, 3, 4 };
	a[1] = z;
	z = a[2];
	return z;
    }
    
}
