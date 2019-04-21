class basic_outer1 {
    class basic_inner1 {
        class basic_inner_inner1 { }
    }
}
class basic_outer2 extends basic_outer1 {
    class basic_inner2 extends basic_inner1 {
        class basic_inner_inner2 extends basic_inner_inner1 { }
    }
}
