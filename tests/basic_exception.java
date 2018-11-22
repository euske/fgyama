public class basic_exception {

    class A extends Exception {
        public String text;
    }

    public void foo() {
        String x = null;
        try {
            x = moo();
            x = "b";
        } catch (A e) {
            x = e.text;
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public String moo() throws A {
        if (true) throw new A();
        return "a";
    }
}
