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
    public List<DFLink> send;
    public List<DFLink> recv;
    
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
	//assert this.recv.size() == 1;
    }

    public DFLink connect(DFNode dst, int lid) {
	return this.connect(dst, lid, DFLinkType.DataFlow);
    }
    
    public DFLink connect(DFNode dst, int lid, DFLinkType type) {
	return this.connect(dst, lid, type, null);
    }
    
    public DFLink connect(DFNode dst, int lid, String label) {
	return this.connect(dst, lid, DFLinkType.DataFlow, label);
    }
    
    public DFLink connect(DFNode dst, int lid, DFLinkType type, String label) {
	DFLink link = new DFLink(this, dst, lid, type, label);
	this.send.add(link);
	dst.recv.add(link);
	//assert (dst.recv.size() == lid);
	return link;
    }

    public void remove() {
        List<DFLink> removed = new ArrayList<DFLink>();
        for (DFLink link : this.send) {
            if (link.src == this) {
                removed.add(link);
            }
        }
        for (DFLink link : this.recv) {
            if (link.dst == this) {
                removed.add(link);
            }
        }
        for (DFLink link : removed) {
            link.disconnect();
        }
        this.scope.graph.removeNode(this);
    }

    public DFLink[] links() {
	DFLink[] links = new DFLink[this.send.size()];
	this.send.toArray(links);
	Arrays.sort(links);
	return links;
    }

}
