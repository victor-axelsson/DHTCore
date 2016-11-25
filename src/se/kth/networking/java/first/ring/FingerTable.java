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
    private List<Long> fingers;
    private static final int RING_BIT_SIZE = 32;
    private List<Node> table;
    RingHandler ringHandler;

    public FingerTable(Node self, RingHandler ringHandler) {
        this.self = self;
        this.ringHandler = ringHandler;
        fingers = new ArrayList<>();

        setupFingerKeys();
    }

    private void setupFingerKeys(){
        for(int i = 0; i < RING_BIT_SIZE; i++){
            fingers.add((self.getId() + (long)Math.pow(2, i) - 1) % (long)Math.pow(2, RING_BIT_SIZE));
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


        for(int i = 0; i < keys.size(); i++){
            if(ringHandler.isThisOurKey(keys.get(i))){
                jsonMessage.getJSONArray("fingers").put(new JSONObject(self.toString()));
                keys.remove(self.getId());
                jsonMessage.remove("keys");
                jsonMessage.put("keys", keys);
            }
        }

        if(keys.isEmpty()){
            Node sender = new Node(jsonMessage.toString());
            jsonMessage.put("type", "finger_probe_response");
            onDone.onResponse(jsonMessage.toString(), sender);
        }

        return jsonMessage.toString();
    }

    public void updateTable(String clientMessage) {
        JSONObject response = new JSONObject(clientMessage);
        JSONArray fingers = response.getJSONArray("fingers");
        table = new ArrayList<>();

        for(int i = 0; i < fingers.length(); i++){
            table.add(new Node(fingers.get(i).toString()));
        }


    }
}
