public class basic_exception {

    class A extends Exception {
        public String text;
    }

    public void foo() throws A {
        String x = null;
        try {
            x = moo();
            throw new Exception();
        } catch (A e) {
            x = e.text;
            throw e;
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public String moo() throws A {
        if (true) throw new A();
        return "a";
    }
}
