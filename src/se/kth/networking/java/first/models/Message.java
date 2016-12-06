package se.kth.networking.java.first.models;

/**
 * Created by Nick on 12/6/2016.
 */
public class Message {
    private final Node from;
    private final Node to;
    private final String message;
    private final OnResponse onResponse;

    public Message(Node from, Node to, String message, OnResponse onResponse) {
        this.from = from;
        this.to = to;
        this.message = message;
        this.onResponse = onResponse;
    }

    public Node getFrom() {
        return from;
    }

    public Node getTo() {
        return to;
    }

    public String getMessage() {
        return message;
    }

    public OnResponse getOnResponse() {
        return onResponse;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Message message1 = (Message) o;
        return (message != null ? !message.equals(message1.message) : message1.message != null);
    }

    @Override
    public int hashCode() {
        int result = message != null ? message.hashCode() : 0;
        return result;
    }
}
