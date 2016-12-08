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

    public MonitorModel(OnResponse<String> onDead, String key, Node node) {
        this.onDead = onDead;
        this.key = key;
        this.node = node;
    }

    public OnResponse<String> getOnDead() {
        return onDead;
    }

    public String getKey() {
        return key;
    }

    public Node getNode() {
        return node;
    }
}
