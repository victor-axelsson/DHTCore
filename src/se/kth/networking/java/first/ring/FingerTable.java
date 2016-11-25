package se.kth.networking.java.first.ring;

import se.kth.networking.java.first.models.Node;

/**
 * Created by Nick on 11/25/2016.
 */
public class FingerTable {
    public static long SUCCESSOR_OFFSET = 2L;
    public static long EIGHTH_RING_OFFSET = 536870912L;
    public static long QUARTER_RING_OFFSET = 1073741824L;
    public static long HALF_RING_OFFSET = 2147483648L;
    public static long RING_SIZE = 4294967296L;

    private Node self;
    private Node successor;
    private Node eighth;
    private Node quarter;
    private Node half;

    public FingerTable(Node self, Node successor) {
        this.self = self;
        this.successor = successor;
    }

    public Node getNodeByKey(long key) {
        long selfKey = self.getId();

        if (selfKey == key) return self;
        if ((selfKey + SUCCESSOR_OFFSET - 1) % RING_SIZE == key) return successor;
        if ((selfKey + EIGHTH_RING_OFFSET - 1) % RING_SIZE == key) return eighth;
        if ((selfKey + QUARTER_RING_OFFSET - 1) % RING_SIZE == key) return quarter;
        if ((selfKey + HALF_RING_OFFSET - 1) % RING_SIZE == key) return half;

        return null;
        //todo check which node is closest
    }
}
