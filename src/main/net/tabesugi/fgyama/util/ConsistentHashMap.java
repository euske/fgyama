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
    public void clear() {
        _keys.clear();
        super.clear();
    }

    @Override
    public V put(K key, V value) {
        if (value != null && this.get(key) == null) {
            _keys.add(key);
        } else if (value == null && this.get(key) != null) {
            _keys.remove(key);
        }
        return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            if (e.getValue() != null && this.get(e.getKey()) == null) {
                _keys.add(e.getKey());
            } else if (e.getValue() == null && this.get(e.getKey()) != null) {
                _keys.remove(e.getKey());
            }
        }
        super.putAll(m);
    }

    @Override
    public V remove(Object key) {
        if (this.get(key) != null) {
            _keys.remove(key);
        }
        return super.remove(key);
    }

    public List<K> keys() {
        return _keys;
    }
}
