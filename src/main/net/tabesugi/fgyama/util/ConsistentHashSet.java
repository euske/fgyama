//  Java2DF
//
package net.tabesugi.fgyama;
import java.util.*;


//  ConsistentHashSet
//
public class ConsistentHashSet<E> implements Set<E>, Iterable<E> {

    private Set<E> _set;
    private List<E> _list;

    public ConsistentHashSet() {
        _set = new HashSet<E>();
        _list = new ArrayList<E>();
    }

    public ConsistentHashSet(ConsistentHashSet<E> c) {
        _set = new HashSet<E>(c._set);
        _list = new ArrayList<E>(c._list);
    }

    public E get(int i) {
        return _list.get(i);
    }

    @Override
    public String toString() {
        return _set.toString();
    }

    @Override
    public boolean add(E e) {
        if (!_set.contains(e)) {
            _list.add(e);
        }
        return _set.add(e);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        for (E e : c) {
            if (!_set.contains(e)) {
                _list.add(e);
            }
        }
        return _set.addAll(c);
    }

    @Override
    public void clear() {
        _set.clear();
        _list.clear();
    }

    @Override
    public boolean contains(Object o) {
        return _set.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return _set.containsAll(c);
    }

    @Override
    public boolean isEmpty() {
        return _set.isEmpty();
    }

    @Override
    public Iterator<E> iterator() {
        return _list.iterator();
    }

    @Override
    public boolean remove(Object o) {
        if (_set.contains(o)) {
            _list.remove(o);
        }
        return _set.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        for (E e : _set) {
            if (c.contains(e)) {
                _list.remove(e);
            }
        }
        return _set.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        for (E e : _set) {
            if (!c.contains(e)) {
                _list.remove(e);
            }
        }
        return _set.retainAll(c);
    }

    @Override
    public int size() {
        return _list.size();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return _list.toArray(a);
    }

    @Override
    public Object[] toArray() {
        return _list.toArray();
    }
}
