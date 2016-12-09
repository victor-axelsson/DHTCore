package se.kth.networking.java.first.monitor;

import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;

/**
 * Created by victoraxelsson on 2016-12-08.
 */
public class MonitorModel {
    private final OnResponse<String> onDead;
    private final String key;
    private final Node node;

    /**
     * Constructor for Monitor model
     * @param onDead - callback method to be executed in case the node is not responsive
     * @param key - a key that should be used to store this MonitorModel. Only one MonitorModel with the same key
     *            should be tracked in the Monitor
     * @param node - node that will be checked for responsiveness
     */
    public MonitorModel(OnResponse<String> onDead, String key, Node node) {
        this.onDead = onDead;
        this.key = key;
        this.node = node;
    }

    /**
     * Getter for onDead
     * @return onDead
     */
    public OnResponse<String> getOnDead() {
        return onDead;
    }

    /**
     * Getter for key
     * @return key
     */

    public String getKey() {
        return key;
    }

    /**
     * Getter for node
     * @return node
     */
    public Node getNode() {
        return node;
    }
}
