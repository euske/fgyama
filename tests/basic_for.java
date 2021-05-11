public class basic_for {

    public static int foo(int n) {
        int x = 0;
        for (int i = 0; i < n; i += 1) {
            x += i;
        }
        return x;
    }

    public static int baa(int[] a) {
        int s = 0;
        for (int x : a) {
            s += x;
        }
        return s;
    }
}
