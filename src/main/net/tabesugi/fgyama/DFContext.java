//  Java2DF
//
package net.tabesugi.fgyama;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFContext
//
public class DFContext {

    private DFGraph _graph;
    private DFVarScope _scope;

    private ConsistentHashMap<DFRef, DFNode> _first =
        new ConsistentHashMap<DFRef, DFNode>();
    private Map<DFRef, DFNode> _last =
        new HashMap<DFRef, DFNode>();

    public DFContext(DFGraph graph, DFVarScope scope) {
        _graph = graph;
        _scope = scope;
    }

    // get(ref): get a current value of the context if defined.
    public DFNode get(DFRef ref) {
        DFNode node = _last.get(ref);
        if (node == null) {
            assert !_first.containsKey(ref);
            node = new RelayNode(_graph, _scope, ref);
            _last.put(ref, node);
            _first.put(ref, node);
        }
        return node;
    }

    public DFNode[] getFirsts() {
        DFNode[] values = new DFNode[_first.size()];
        int i = 0;
        for (DFRef ref : _first.keys()) {
            values[i++] = _first.get(ref);
        }
        return values;
    }

    public DFNode getLast(DFRef ref) {
        return _last.get(ref);
    }

    public void set(DFNode node) {
        DFRef ref = node.getRef();
        assert ref != null;
        _last.put(ref, node);
    }

    // dump: for debugging.
    public void dump() {
        dump(System.err);
    }
    public void dump(PrintStream out) {
        out.println("DFContext");
        out.println("  firsts:");
        for (Map.Entry<DFRef, DFNode> ent : _first.entrySet()) {
            out.println("    "+ent.getKey()+" = "+ent.getValue());
        }
        out.println("  lasts:");
        for (Map.Entry<DFRef, DFNode> ent : _last.entrySet()) {
            out.println("    "+ent.getKey()+" = "+ent.getValue());
        }
    }
}


//  RelayNode
//
class RelayNode extends DFNode {

    public RelayNode(
        DFGraph graph, DFVarScope scope, DFRef ref) {
        super(graph, scope, ref.getRefType(), ref, null);
    }
}
