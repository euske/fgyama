public class basic_funcs {

    public static void main(String[] args) {
        String a = args[0];
        int b = Integer.parseInt(a);
        int c = moo(b);
        System.out.println(c);
    }

    public static int moo(int x) {
        if (x == 0) {
            return foo();
        } else {
            return moo(x-1)+2;
        }
    }

    public static int foo() {
        return 3;
    }
}
