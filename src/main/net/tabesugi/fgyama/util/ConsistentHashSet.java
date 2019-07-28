//  Java2DF
//
package net.tabesugi.fgyama;
import java.util.*;


//  ConsistentHashSet
//
public class ConsistentHashSet<E> extends HashSet<E> implements Iterable<E> {

    private static final long serialVersionUID = 1L;

    private List<E> _elems = new ArrayList<E>();

    @Override
    public boolean add(E e) {
        _elems.add(e);
        return super.add(e);
    }

    @Override
    public void clear() {
        _elems.clear();
        super.clear();
    }

    @Override
    public boolean remove(Object o) {
        if (this.contains(o)) {
            _elems.remove(o);
        }
        return super.remove(o);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        for (E e : c) {
            if (!this.contains(e)) {
                _elems.add(e);
            }
        }
        return super.addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        for (E e : this) {
            if (!c.contains(e)) {
                _elems.remove(e);
            }
        }
        return super.retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        for (E e : this) {
            if (c.contains(e)) {
                _elems.remove(e);
            }
        }
        return super.removeAll(c);
    }

    @Override
    public Iterator<E> iterator() {
        return _elems.iterator();
    }
}
