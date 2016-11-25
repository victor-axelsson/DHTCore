package se.kth.networking.java.first.ring;

import org.json.JSONArray;
import org.json.JSONObject;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Nick on 11/25/2016.
 */
public class FingerTable {

    private Node self;
    private Node successor;
    private List<Long> fingers;
    private static final int RING_BIT_SIZE = 32;

    public FingerTable(Node self, Node successor) {
        this.self = self;
        this.successor = successor;
        fingers = new ArrayList<>();
        setupFingerKeys();
    }

    private void setupFingerKeys(){
        for(int i = 0; i < RING_BIT_SIZE; i++){
            fingers.add((self.getId() + 2^i - 1) % 2 ^ RING_BIT_SIZE);
        }
    }

    public String createFingerProbeMessage(){
        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());
        message.put("type", "finger_probe");
        message.put("keys", new JSONArray(fingers.toArray()));
        message.put("fingers", new JSONArray());

        return message.toString();
    }

    public String dealWithFingerProbe(String message, OnResponse<String> onDone){
        JSONObject jsonMessage = new JSONObject(message);
        List<Long> keys = (List<Long>)(List<?>)jsonMessage.getJSONArray("keys").toList();

        if(keys.contains(self.getId())){
            jsonMessage.getJSONArray("fingers").put(new JSONObject(self.toString()));
            keys.remove(self.getId());
            jsonMessage.remove("keys");
            jsonMessage.put("keys", keys);
        }

        if(keys.isEmpty()){
            Node sender = new Node(jsonMessage.toString());
            onDone.onResponse(jsonMessage.toString(), sender);
        }

        return jsonMessage.toString();
    }

}
