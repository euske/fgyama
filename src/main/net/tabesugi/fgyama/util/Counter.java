//  Java2DF
//
package net.tabesugi.fgyama;


//  Counter
//
public class Counter {

    int _baseId;

    public Counter(int baseId) {
        _baseId = baseId;
    }

    public int getNewId() {
        return _baseId++;
    }
}
