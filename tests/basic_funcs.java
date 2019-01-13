public class basic_funcs {

    public int y = 0;
    public int z = 0;

    public static void main(String[] args) {
        String a = args[0];
        int b = Integer.parseInt(a);
        int c = moo(b);
        System.out.println(c);
    }

    public static int moo(int x) {
        z += 9;
        if (x == 0) {
            return foo();
        } else {
            return moo(x-1)+2;
        }
    }

    public static int foo() {
        z = 7;
        return y;
    }
}
