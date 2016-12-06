package se.kth.networking.java.first.ring;

import org.json.JSONObject;
import se.kth.networking.java.first.Helper;
import se.kth.networking.java.first.models.Message;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;
import se.kth.networking.java.first.network.Client;

import java.io.IOException;
import java.util.*;

/**
 * Created by victoraxelsson on 2016-12-01.
 */
public class SocketQueue {
    private final Timer resendTimer;
    private HashMap<String, Message> messagesQueue;
    private Node parent;

    public SocketQueue(Node parent){
        messagesQueue = new HashMap<>();
        this.parent = parent;

        TimerTask fingers = new TimerTask() {
            @Override
            public void run() {
                try {
                    if (messagesQueue.size() > 0) trySend();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        int interval2 = 2000;
        int delay2 = Helper.getHelper().getRandom(10, interval2);

        resendTimer = new Timer();
        resendTimer.scheduleAtFixedRate(fingers, delay2, interval2);
    }

    public void sendMessage(Node node, String message, OnResponse onResponse) throws IOException {
        if (parent.getId().equals(node.getId()) && !isProbe(message)) {
            System.out.println("Added message to holdback queue " + messagesQueue.size());
            messagesQueue.put(message, new Message(parent, node, message, onResponse));
        }
        else {
            System.out.println("----");
            System.out.println("MSG: " + message);
            System.out.println("TO:" + node.getPort());
            System.out.println("----");

            Client c = null;
            c = new Client(node.getAsSocket(), message, onResponse);
            c.start();
        }
    }

    private boolean isProbe(String message) {
        return "probe".equals(new JSONObject(message).getString("type"));
    }

    public void trySend() throws IOException {
        System.out.println("Empty holdback queue");
        List<Message> messagesToSend = new ArrayList<>(messagesQueue.values());
        messagesQueue.clear();
        for (Message message : messagesToSend) {
            sendMessage(message.getTo(), message.getMessage(), message.getOnResponse());
        }
    }

    public void stop() {
        resendTimer.cancel();
        resendTimer.purge();
    }

}
