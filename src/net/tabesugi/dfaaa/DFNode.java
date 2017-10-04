//  Java2DF
//
package net.tabesugi.dfaaa;
import java.io.*;
import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;


//  DFNode
//
public abstract class DFNode implements Comparable<DFNode> {

    public DFScope scope;
    public DFRef ref;
    public int id;
    public String name;
    
    private List<DFLink> send;
    private List<DFLink> recv;
    
    public DFNode(DFScope scope, DFRef ref) {
	this.scope = scope;
	this.id = this.scope.graph.addNode(this);
	this.ref = ref;
	this.send = new ArrayList<DFLink>();
	this.recv = new ArrayList<DFLink>();
    }

    @Override
    public String toString() {
	return ("<DFNode("+this.name()+") "+this.label()+">");
    }

    @Override
    public int compareTo(DFNode node) {
	return this.id - node.id;
    }
    
    public String name() {
	return ("N"+this.scope.name+"_"+id);
    }

    abstract public DFNodeType type();

    abstract public String label();

    public boolean canOmit() {
	return false;
    }

    public void accept(DFNode node) {
	node.connect(this, 1);
	assert this.recv.size() == 1;
    }

    public void connect(DFNode dst, int lid) {
	this.addLink(new DFLink(this, dst, lid));
    }
    
    public void connect(DFNode dst, int lid, DFLinkType type) {
	this.addLink(new DFLink(this, dst, lid, type));
    }
    
    public void connect(DFNode dst, int lid, String label) {
	this.addLink(new DFLink(this, dst, lid, DFLinkType.DataFlow, label));
    }
    
    public void connect(DFNode dst, int lid, DFLinkType type, String label) {
	this.addLink(new DFLink(this, dst, lid, type, label));
    }

    public boolean tryRemove() {
	if (this.send.size() == 1 &&
	    this.recv.size() == 1) {
            DFLink link0 = this.recv.get(0);
            DFLink link1 = this.send.get(0);
            if (link0.type == link1.type &&
		(link0.label == null || link1.label == null)) {
		DFNode src = link0.src;
		DFNode dst = link1.dst;
                String label = (link0.label != null)? link0.label : link1.label;
                this.remove();
		src.connect(dst, link1.lid, link1.type, label);
		return true;
            }
	}
	return false;
    }

    private void remove() {
        List<DFLink> removed = new ArrayList<DFLink>();
        for (DFLink link : this.send) {
	    assert link.src == this;
	    removed.add(link);
        }
        for (DFLink link : this.recv) {
            assert link.dst == this;
	    removed.add(link);
        }
        for (DFLink link : removed) {
	    link.src.send.remove(link);
	    link.dst.recv.remove(link);
        }
    }

    public DFLink[] links() {
	DFLink[] links = new DFLink[this.send.size()];
	this.send.toArray(links);
	Arrays.sort(links);
	return links;
    }

    private void addLink(DFLink link) {
	assert !link.dst.containsLid(link.lid);
	link.src.send.add(link);
	link.dst.recv.add(link);
    }

    private boolean containsLid(int lid) {
	if (lid != 0) {
	    for (DFLink link : this.recv) {
		if (link.lid == lid) return true;
	    }
	}
	return false;
    }

}
