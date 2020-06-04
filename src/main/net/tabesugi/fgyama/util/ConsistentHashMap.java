//  Java2DF
//
package net.tabesugi.fgyama;
import java.util.*;


//  ConsistentHashMap
//
public class ConsistentHashMap<K,V> implements Map<K,V> {

    private Map<K,V> _map = new HashMap<K,V>();
    private List<K> _keys = new ArrayList<K>();

    @Override
    public String toString() {
        return _map.toString();
    }

    @Override
    public void clear() {
        _map.clear();
        _keys.clear();
    }

    @Override
    public boolean containsKey(Object key) {
        return _map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return _map.containsValue(value);
    }

    @Override
    public Set<Map.Entry<K,V>> entrySet() {
        return _map.entrySet();
    }

    @Override
    public V get(Object key) {
        return _map.get(key);
    }

    @Override
    public boolean isEmpty() {
        return _map.isEmpty();
    }

    @Override
    public Set<K> keySet() {
        return _map.keySet();
    }

    @Override
    public V put(K key, V value) {
        if (value != null && _map.get(key) == null) {
            _keys.add(key);
        } else if (value == null && _map.get(key) != null) {
            _keys.remove(key);
        }
        return _map.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            K key = e.getKey();
            V value = e.getValue();
            if (value != null && _map.get(key) == null) {
                _keys.add(key);
            } else if (value == null && _map.get(key) != null) {
                _keys.remove(key);
            }
        }
        _map.putAll(m);
    }

    @Override
    public V remove(Object key) {
        if (_map.get(key) != null) {
            _keys.remove(key);
        }
        return _map.remove(key);
    }

    @Override
    public int size() {
        return _map.size();
    }

    @Override
    public List<V> values() {
        List<V> a = new ArrayList<V>();
        for (K key : _keys) {
            a.add(_map.get(key));
        }
        return a;
    }

    public List<K> keys() {
        return _keys;
    }
}
