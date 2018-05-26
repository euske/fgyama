package dom.meep;

public class multi_xref2 {

    multi_xref1 xref1;

    public void foo() {
	this.xref1 = new multi_xref1();
	xref1.moo();
        multi_xref1.baa zzz;
        int b = zzz.baz;
        b = dom.dood.multi_xref3.boo;
    }

    public static int bam() {
        return 42;
    }
}
