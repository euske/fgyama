public class basic_ops {

    public static int foo(int x) {
	int z = x + 1;
        z *= 2;
	z = moo(z, 3);
	int[] a = new int[10] { 44, 55, 66 };
	a[1] = z;
	z = a[2];
	return z;
    }
}
