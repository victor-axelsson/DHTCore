package se.kth.networking.java.first.ring;

import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;
import se.kth.networking.java.first.network.Client;

/**
 * Created by victoraxelsson on 2016-12-01.
 */
public class Message {
    Node node;
    String message;
    OnResponse onResponse;
    Client client;

    public Message(Client client, Node node, String message, OnResponse onResponse) {
        this.node = node;
        this.message = message;
        this.onResponse = onResponse;
        this.client = client;
    }

    public Node getNode() {
        return node;
    }

    public String getMessage() {
        return message;
    }

    public OnResponse getOnResponse() {
        return onResponse;
    }

    public Client getClient() {
        return client;
    }
}
