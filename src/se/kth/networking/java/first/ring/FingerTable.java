package se.kth.networking.java.first.ring;

import org.json.JSONArray;
import org.json.JSONObject;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Nick on 11/25/2016.
 */
public class FingerTable {

    private Node self;
    private List<BigInteger> fingers;
    private static final int RING_BIT_SIZE = 128;
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
            fingers.add((self.getId().add(new BigInteger(String.valueOf(2)).pow(i)).subtract(BigInteger.ONE))
                    .mod(new BigInteger(String.valueOf(2)).pow(RING_BIT_SIZE)));
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
        List<Object> keys = jsonMessage.getJSONArray("keys").toList();

        List<BigInteger> notFoundKeys = new ArrayList<>();

        for(int i = 0; i < keys.size(); i++){
            BigInteger key = new BigInteger(keys.get(i).toString());
            if(ringHandler.isThisOurKey(key)){
                jsonMessage.getJSONArray("fingers").put(new JSONObject(self.toString()));
            } else {
                notFoundKeys.add(key);
            }
        }

        jsonMessage.remove("keys");
        jsonMessage.put("keys", notFoundKeys);

        if(notFoundKeys.isEmpty() || jsonMessage.getString("ip").equals(self.getIp()) && jsonMessage.getInt("port") == self.getPort()){
            Node sender = new Node(jsonMessage.toString());
            jsonMessage.put("type", "finger_probe_response");
            onDone.onResponse(jsonMessage.toString(), sender);
        }

        return jsonMessage.toString();
    }

    public synchronized void updateTable(String clientMessage) {
        JSONObject response = new JSONObject(clientMessage);
        JSONArray fingers = response.getJSONArray("fingers");
        table = new ArrayList<>();

        for(int i = 0; i < fingers.length(); i++){
            table.add(new Node(fingers.get(i).toString()));
        }

        System.out.println("Table is " + table);
    }

    public List<Node> getTable() {
        return table;
    }
}
