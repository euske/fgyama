import java.util.Map;
import java.util.EnumMap;

enum E { }

public class regression_defaultklass {

    static void foo() {
        Map a = new EnumMap(E.class);
    }
}
