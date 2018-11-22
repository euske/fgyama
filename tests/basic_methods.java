public class basic_methods {

    class A {
        public String toString() {
            return "A";
        }
    }

    int x = 0;
    public void fa() {
	A a = new A() {
	    public String toString() { return "AAA"; }
	};
	class moo {
	    public int fc() { return basic_methods.this.x; }
	}
        x = fb();
        a.toString();
        moo c = new moo();
        c.fc();
    }
    public static int fb() {
	int a = 0;
        return a;
    }
}
