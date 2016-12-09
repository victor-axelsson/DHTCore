package se.kth.networking.java.first.ring;

import org.json.JSONArray;
import org.json.JSONObject;
import se.kth.networking.java.first.Env;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;

import java.io.IOException;
import java.io.PrintWriter;
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

    /**
     * Constructor for FingerTable instance
     * @param self - the node that will use this instance of a finger table
     * @param ringHandler - instance of RingHandler that is associated with self
     */
    public FingerTable(Node self, RingHandler ringHandler) {
        this.self = self;
        this.ringHandler = ringHandler;
        fingers = new ArrayList<>();

        setupFingerKeys();
    }

    /**
     * Initial method to populate the fingers list with key values, that are offsets from the current node key by
     * powers of 2 up to 128
     */
    private void setupFingerKeys() {
        for (int i = 0; i < RING_BIT_SIZE; i++) {
            fingers.add((self.getId().add(new BigInteger(String.valueOf(2)).pow(i)).subtract(BigInteger.ONE))
                    .mod(new BigInteger(String.valueOf(2)).pow(RING_BIT_SIZE)));
        }
    }

    /**
     * Helper method to create the initial message for finger probe call that will populate the table with the actual
     * nodes that are responsible for keys in the fingers list
     * @return String representing a finger probe message from the current node
     */
    public String createFingerProbeMessage() {
        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());
        message.put("type", "finger_probe");

        List<BigInteger> keys = new ArrayList<>();
        List<JSONObject> myKeys = new ArrayList<>();


        for (int i = 0; i < fingers.size(); i++) {
            if (!ringHandler.isThisOurKey(fingers.get(i))){
                keys.add(fingers.get(i));
            } else{
                myKeys.add(new JSONObject(self));
            }
        }
        message.put("keys", keys);

        message.put("fingers", myKeys);

        return message.toString();
    }

    /**
     * Helper method to handle a finger probe message
     * @param message - String representing the received message
     * @param onDone - callback method to be executed, when all of the fingers have been found
     * @return a String that represents the finger probe message after adding all of the fingers from the current Node
     */
    public String dealWithFingerProbe(String message, OnResponse<String> onDone) {
        JSONObject jsonMessage = new JSONObject(message);


        List<Object> keys = jsonMessage.getJSONArray("keys").toList();
        List<BigInteger> notFoundKeys = new ArrayList<>();

        for (int i = 0; i < keys.size(); i++) {
            BigInteger key = new BigInteger(keys.get(i).toString());
            if (ringHandler.isThisOurKey(key)) {
                jsonMessage.getJSONArray("fingers").put(new JSONObject(self.toString()));
            } else {
                notFoundKeys.add(key);
            }
        }

        jsonMessage.remove("keys");
        jsonMessage.put("keys", notFoundKeys);

        if (notFoundKeys.isEmpty() || jsonMessage.getString("ip").equals(self.getIp()) && jsonMessage.getInt("port") == self.getPort()) {
            Node sender = new Node(jsonMessage.toString());
            jsonMessage.put("type", "finger_probe_response");

            onDone.onResponse(jsonMessage.toString(), sender);
        }

        return jsonMessage.toString();
    }

    /**
     * Method that is called, when we finish finding all of the fingers - it populates the table with the corresponding
     * nodes
     * @param clientMessage - message that was received after all of the fingers have been found
     */
    public synchronized void updateTable(String clientMessage) {
        JSONObject response = new JSONObject(clientMessage);
        JSONArray fingers = response.getJSONArray("fingers");
        table = new ArrayList<>();

        for (int i = 0; i < fingers.length(); i++) {
            table.add(new Node(fingers.get(i).toString()));
        }

        System.out.println("Table is " + table);

       // saveTable();
    }

    /**
     * Method to save the table of fingers in a file
     */
    private void saveTable(){

        List<String> env = Env.getEnv();

        String path = env.get(0);
        String sepparator = env.get(1);


        try{
            PrintWriter writer = new PrintWriter( ringHandler.getSelf().getPort() + "table.csv", "UTF-8");

            for(int i = 0; i < table.size(); i++){
                Node n = table.get(i);
                BigInteger key = fingers.get(i);
                writer.println(key + sepparator + n.getId() + sepparator + n.getIp() + sepparator + n.getPort());
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace(System.err);
            // do something
        }

    }

    /**
     * Getter for table
     * @return table
     */
    public List<Node> getTable() {
        return table;
    }
}
