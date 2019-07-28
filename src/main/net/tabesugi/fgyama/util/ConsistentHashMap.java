//  Java2DF
//
package net.tabesugi.fgyama;
import java.util.*;


//  ConsistentHashMap
//
public class ConsistentHashMap<K,V> extends HashMap<K,V> {

    private static final long serialVersionUID = 1L;

    private List<K> _keys = new ArrayList<K>();

    @Override
    public V put(K key, V value) {
        if (value != null && this.get(key) == null) {
            _keys.add(key);
        } else if (value == null && this.get(key) != null) {
            _keys.remove(key);
        }
        return super.put(key, value);
    }

    public List<K> keys() {
        return _keys;
    }
}
