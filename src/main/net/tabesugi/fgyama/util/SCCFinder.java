//  SCCFinder.java
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import java.util.function.*;

public class SCCFinder<T> {

    public class SCC {

        public int cid;
        public List<T> items;
        public List<SCC> to = new ArrayList<SCC>();
        public List<SCC> from = new ArrayList<SCC>();
        private boolean _fixated = false;

        public SCC(int cid, List<T> items) {
            this.cid = cid;
            this.items = items;
        }

        @Override
        public String toString() {
            return "<C"+this.cid+":"+this.items+">";
        }

        private void fixate() {
            if (_fixated) return;
            _fixated = true;
            for (T v0 : items) {
                for (T v1 : _mapper.get(v0)) {
                    SCC scc = _item2scc.get(v1);
                    if (scc != this) {
                        this.to.add(scc);
                        scc.from.add(this);
                    }
                }
            }
        }
    }

    interface Mapper<T> {
        Collection<T> get(T n);
    }
    private Mapper<T> _mapper;

    private ConsistentHashSet<T> _stack = new ConsistentHashSet<T>();
    private Map<T, Integer> _nodenum = new HashMap<T, Integer>();
    private Map<T, Integer> _lowlink = new HashMap<T, Integer>();
    private List<SCC> _sccs = new ArrayList<SCC>();
    private Map<T, SCC> _item2scc = new HashMap<T, SCC>();

    public SCCFinder(Mapper<T> mapper) {
        _mapper = mapper;
    }

    public void add(T[] items) {
        for (T v : items) {
            this.add(v);
        }
    }

    public void add(Iterable<T> items) {
        for (T v : items) {
            this.add(v);
        }
    }

    public void add(T v0) {
        if (_nodenum.containsKey(v0)) return;
        int index = _nodenum.size()+1;
        _stack.add(v0);
        _nodenum.put(v0, index);
        _lowlink.put(v0, index);
        for (T v1 : _mapper.get(v0)) {
            if (!_nodenum.containsKey(v1)) {
                this.add(v1);   // recursive DFS.
                _lowlink.put(v0, Math.min(_lowlink.get(v0), _lowlink.get(v1)));
            } else if (_stack.contains(v1)) {
                _lowlink.put(v0, Math.min(_lowlink.get(v0), _nodenum.get(v1)));
            }
        }
        if (_nodenum.get(v0) == _lowlink.get(v0)) {
            List<T> items = new ArrayList<T>();
            while (true) {
                T v = _stack.get(_stack.size()-1);
                _stack.remove(v);
                items.add(v);
                if (v == v0) break;
            }
            int cid = _sccs.size()+1;
            SCC scc = new SCC(cid, items);
            _sccs.add(scc);
            for (T v : items) {
                _item2scc.put(v, scc);
            }
        }
    }

    public List<SCC> getSCCs() {
        for (SCC scc : _sccs) {
            scc.fixate();
        }
        return _sccs;
    }

    public static void main(String[] args) {
        class V {
            String name;
            List<V> to = new ArrayList<V>();
            V(String name) { this.name = name; }
            void linkTo(V v) { this.to.add(v); }
            @Override
            public String toString() {
                return "<"+this.name+">";
            }
        };
        SCCFinder<V> f = new SCCFinder<V>(v -> v.to);
        V[] v = new V[] {
            new V("v0"), new V("v1"), new V("v2"), new V("v3"),
            new V("v4"), new V("v5"), new V("v6"), new V("v7"),
        };
        v[0].linkTo(v[1]);
        v[1].linkTo(v[2]);
        v[2].linkTo(v[0]);
        v[3].linkTo(v[1]);
        v[3].linkTo(v[2]);
        v[3].linkTo(v[4]);
        v[4].linkTo(v[3]);
        v[4].linkTo(v[5]);
        v[5].linkTo(v[2]);
        v[5].linkTo(v[6]);
        v[6].linkTo(v[5]);
        v[7].linkTo(v[4]);
        v[7].linkTo(v[6]);
        v[7].linkTo(v[7]);
        f.add(v);
        for (SCCFinder<V>.SCC scc : f.getSCCs()) {
            System.out.println(scc);
        }
    }
}
