import java.io.*;

class Record {
    String key;
    String value;
    public Record(String key, String value) {
        this.key = key;
        this.value = value;
    }
}

class Listy<T extends Object> {
    private int _n = 0;
    private int _max = 0;
    private Object[] _objs = null;

    public void add(T x) {
        if (_objs == null || _max <= _n) {
            _max = (_max*2)+1;
            Object[] objs = new Object[_max];
            if (_objs != null) {
                for (int i = 0; i < _n; i++) {
                    objs[i] = _objs[i];
                }
            }
            _objs = objs;
        }
        _objs[_n++] = x;
    }

    @SuppressWarnings("unchecked")
    public T get(int i) {
        if (0 <= i && i < _n) {
            return (T)_objs[i];
        }
        return null;
    }

    public int size() {
        return _n;
    }
}

class DB {
    private Listy<Record> _a = new Listy<Record>();

    public DB(String path) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(path));
        while (true) {
            String line = reader.readLine();
            if (line == null) break;
            int i = line.indexOf(' ');
            String key = line.substring(0, i);
            String value = line.substring(i+1);
            Record rec = new Record(key, value);
            _a.add(rec);
        }
    }

    public String get(String key) {
        for (int i = 0; i < _a.size(); i++) {
            Record rec = _a.get(i);
            if (rec.key.equals(key)) {
                return rec.value;
            }
        }
        return null;
    }
}

public class App1 {
    public static void main(String[] args) throws IOException {
        DB db = new DB(args[0]);
        for (int i = 1; i < args.length; i++) {
            String v = db.get(args[i]);
            System.out.println(v);
        }
    }
}
