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
    
    private List<DFLink> outgoing;
    private List<DFLink> incoming;
    
    public DFNode(DFScope scope, DFRef ref) {
	this.scope = scope;
	this.id = this.scope.graph.addNode(this);
	this.ref = ref;
	this.outgoing = new ArrayList<DFLink>();
	this.incoming = new ArrayList<DFLink>();
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
	assert this.incoming.size() == 1;
    }

    public void connect(DFNode dst, int deg) {
	this.addLink(new DFLink(this, dst, deg));
    }
    
    public void connect(DFNode dst, int deg, DFLinkType type) {
	this.addLink(new DFLink(this, dst, deg, type));
    }
    
    public void connect(DFNode dst, int deg, String label) {
	this.addLink(new DFLink(this, dst, deg, DFLinkType.DataFlow, label));
    }
    
    public void connect(DFNode dst, int deg, DFLinkType type, String label) {
	this.addLink(new DFLink(this, dst, deg, type, label));
    }

    protected void addLink(DFLink link) {
	assert !link.dst.containsDeg(link.deg);
	link.src.outgoing.add(link);
	link.dst.incoming.add(link);
    }

    public DFLink[] links() {
	DFLink[] links = new DFLink[this.outgoing.size()];
	this.outgoing.toArray(links);
	Arrays.sort(links);
	return links;
    }

    public boolean tryRemove() {
	if (this.outgoing.size() == 1 &&
	    this.incoming.size() == 1) {
            DFLink link0 = this.incoming.get(0);
            DFLink link1 = this.outgoing.get(0);
            if (link0.type == link1.type &&
		(link0.label == null || link1.label == null)) {
		DFNode src = link0.src;
		DFNode dst = link1.dst;
                String label = (link0.label != null)? link0.label : link1.label;
                this.remove();
		src.connect(dst, link1.deg, link1.type, label);
		return true;
            }
	}
	return false;
    }

    private void remove() {
        List<DFLink> removed = new ArrayList<DFLink>();
        for (DFLink link : this.outgoing) {
	    assert link.src == this;
	    removed.add(link);
        }
        for (DFLink link : this.incoming) {
            assert link.dst == this;
	    removed.add(link);
        }
        for (DFLink link : removed) {
	    link.src.outgoing.remove(link);
	    link.dst.incoming.remove(link);
        }
    }

    private boolean containsDeg(int deg) {
	if (deg != 0) {
	    for (DFLink link : this.incoming) {
		if (link.deg == deg) return true;
	    }
	}
	return false;
    }

}
