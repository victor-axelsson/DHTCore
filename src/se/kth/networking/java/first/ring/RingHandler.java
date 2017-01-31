package se.kth.networking.java.first.ring;


import org.json.JSONArray;
import org.json.JSONObject;
import se.kth.networking.java.first.ApplicationDomain;
import se.kth.networking.java.first.Helper;
import se.kth.networking.java.first.logger.Logger;
import se.kth.networking.java.first.logger.SocketIOLogger;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;
import se.kth.networking.java.first.monitor.Monitor;
import se.kth.networking.java.first.monitor.MonitorModel;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by victoraxelsson on 2016-11-04.
 */
public class RingHandler {

    private Node predecessor;
    private Node successor;
    private Node nextSuccessor;
    private FingerTable fingers;
    private Node self;
    private ApplicationDomain app;
    private Timer stabilizeTimer;
    private Timer fingerTimer;
    private SocketQueue socketQueue;
    private Monitor monitor;

    /**
     * Setter method for the successor. Should be called every time we need to set the successor
     * @param successor - the new successor Node
     */
    public void setSuccessor(Node successor) {
        this.successor = successor;
    }

    /**
     * Setter method for the predecessor. Should be called every time we need to set the predecessor
     * @param newPredecessor - the new predecessor Node
     */
    public void setPredecessor(Node newPredecessor) {
        this.predecessor = newPredecessor;

        if (newPredecessor!= null && !getSelf().equals(newPredecessor)){

            monitor.addMonitor(new MonitorModel(new OnResponse<String>() {
                @Override
                public String onResponse(String response, Node node) {
                    predecessor = null;
                    return null;
                }
            }, "predecessor", predecessor));
        }
    }

    /**
     * Setter method for the next successor, that is, the successor of our successor. Should be called every time we
     * need to set the next successor
     * @param next - the new next successor Node
     */
    public void setNextSuccessor(Node next){
        this.nextSuccessor = next;

        if (next!= null && !getSelf().equals(next)){
            monitor.addMonitor(new MonitorModel(new OnResponse<String>() {
                @Override
                public String onResponse(String response, Node node) {
                    updateNextSuccessor();
                    System.out.println("next set to null");
                    return null;
                }
            }, "next", nextSuccessor));
        }
    }

    /**
     * Constructor of a new RingHandler instance
     * @param ip - current Node ip address
     * @param port - current Node port
     * @param app - ApplicationDomain implementation that will be used
     */
    public RingHandler(String ip, int port, ApplicationDomain app) {
        this.self = new Node(ip, port);
        this.successor = this.self;
        this.app = app;
        this.fingers = new FingerTable(self, this);
        socketQueue = new SocketQueue();
        monitor = new Monitor();

        TimerTask stabilizeTask = new TimerTask() {
            @Override
            public void run() {
                stabilize();
            }
        };

        //Set a random day so that the stabalizers don't run at the same time
        int interval = 2000;
        int delay = Helper.getHelper().getRandom(200, interval);

        stabilizeTimer = new Timer();
        stabilizeTimer.scheduleAtFixedRate(stabilizeTask, delay, interval);

        TimerTask fingers = new TimerTask() {
            @Override
            public void run() {
                updateFingerTable();
            }
        };

        // Set a random day so that the stabalizers don't run at the same time
        int interval2 = 10000;
        int delay2 = Helper.getHelper().getRandom(interval2/2, interval2);

        fingerTimer = new Timer();

        fingerTimer.scheduleAtFixedRate(fingers, delay2, interval2);
    }

    /**
     * This method cleanly terminates the RingHandler and frees the resources
     */
    public void shutdown() {
        stabilizeTimer.cancel();
        stabilizeTimer.purge();
        monitor.stop();
        fingerTimer.cancel();
        fingerTimer.purge();
        transferStoredData();
    }


    /**
     * This method is called from fingersTimer and is used to initiate a finger table update
     */
    private void updateFingerTable() {
        if(!successor.getId().equals(self.getId()) && predecessor != null &&
                !predecessor.getId().equals(self.getId())){

            JSONObject message = new JSONObject(fingers.createFingerProbeMessage());

            try {
                socketQueue.sendMessage(successor, message.toString(), null);
            } catch (IOException e) {
                e.printStackTrace(System.err);
                handleUnresponsiveSuccessorNode();
            }
        }

    }

    /**
     * A method that facilitates stabilization of the DHT ring it sends out a request message to the successor node
     * and updates the successor and predecessor accordingly to the response
     */
    private void stabilize() {
        if (self.equals(successor)) return;

        JSONObject message = new JSONObject();
        message.put("type", "request");
        message.put("ip", self.getIp());
        message.put("port", self.getPort());

        try {
            socketQueue.sendMessage(successor, message.toString(), new OnResponse<String>() {
                @Override
                public String onResponse(String response, Node node) {

                    if(response == null){
                        return null;
                    }

                    JSONObject jsonResponse = new JSONObject(response);

                    Node otherPredesessor = null;
                    Node otherSuccessor = null;

                    if (!jsonResponse.getString("predecessor").equalsIgnoreCase("null")) {
                        otherPredesessor = new Node(jsonResponse.getString("predecessor"));
                    }

                    if (!jsonResponse.getString("successor").equalsIgnoreCase("null")) {
                        otherSuccessor = new Node(jsonResponse.getString("successor"));
                    }

                    onStabilizeRequest(otherPredesessor, otherSuccessor, node);
                    return null;
                }
            });
        } catch (IOException e) {
            e.printStackTrace(System.err);
            handleUnresponsiveSuccessorNode();
        }
    }

    /**
     * This method is called every time the node receives a request message. It puts its successor and predecessor
     * in the response
     * @return response to the request message
     */
    public String onRequest() {
        JSONObject response = new JSONObject();
        response.put("ip", self.getIp());
        response.put("port", self.getPort());

        if (predecessor == null) {
            response.put("predecessor", "null");
        } else {
            response.put("predecessor", predecessor.toString());
        }

        if (successor == null) {
            response.put("successor", "null");
        } else {
            response.put("successor", successor.toString());
        }

        return response.toString();
    }

    /**
     * This method is called every time we receive a response for out request message during stabilize()
     * @param other - node that we consider to be our possible successor
     * @param otherPredecessor - predecessor of the node we consider our possible successor
     * @param otherSuccessor - successor of the node we consider our possible successor
     */
    public void onStabilizeRequest(Node otherPredecessor, Node otherSuccessor, Node other) {

        if (otherPredecessor == null) {

            // It have no predecessor, notify our successor that its predecessor might be us
            try {
                sendNotify(other.getIp(), other.getPort());
            } catch (IOException e) {
                e.printStackTrace(System.err);
                handleUnresponsiveSuccessorNode();
            }

        } else if (Objects.equals(otherPredecessor.getId(), self.getId())) {
            //All is well, its us.
            System.out.println("it us");

        } else if (Objects.equals(otherPredecessor.getId(), other.getId())) {
            //The successors predesessor is itself, we should probably be there instead
            try {
                sendNotify(other.getIp(), other.getPort());
            } catch (IOException e) {
                e.printStackTrace(System.err);
                handleUnresponsiveSuccessorNode();
            }

        } else {
            if (between(otherPredecessor.getId(), self.getId(), other.getId())) {

                // we probably hve the wrong successor
                try {
                    sendNotify(otherPredecessor.getIp(), otherPredecessor.getPort());
                    setSuccessor(otherPredecessor);
                    setNextSuccessor(otherSuccessor);
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                    e.printStackTrace();
                }
            } else {
                //we should be in between the successor and its predecessor
                try {
                    sendNotify(other.getIp(), other.getPort());
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                    handleUnresponsiveSuccessorNode();
                }
            }
        }
    }

    /**
     * Method that is used to initiate a probe through the nodes of DHT to determine its structure
     */
    public void probe() {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "probe");
            JSONArray nodes = new JSONArray();

            JSONObject selfProbe = new JSONObject(self.toString());
            selfProbe.put("predeccesor", predecessor == null ? "null" : predecessor.getPort());
            selfProbe.put("successor", successor == null ? "null" : successor.getPort());
            selfProbe.put("next", nextSuccessor == null ? "null" : nextSuccessor.getPort());
            nodes.put(selfProbe);

            message.put("nodes", nodes);

            socketQueue.sendMessage(successor, message.toString(), null );

        } catch (IOException e) { //if exception -> node died
            e.printStackTrace(System.err);
            handleUnresponsiveSuccessorNode();
        }
    }

    /**
     * This method is called every time the node receives a probe message
     * @param clientMessage - the current message that represents the probe state
     */
    public void handleProbe(String clientMessage) {
        JSONObject message = new JSONObject(clientMessage);

        JSONArray nodes = message.getJSONArray("nodes");
        if (nodes.length() > 0) {
            String json = nodes.get(0).toString();
            Node initiator = new Node(json);

            //Node initiator = new Node(args[0], Integer.parseInt(args[1]));
            if (self.equals(initiator)) {
                System.out.println("I got it back from the ring, " + clientMessage);
                SocketIOLogger.getLogger().log("probe", clientMessage);
            } else {
                try {
                    JSONObject selfProbe = new JSONObject(self.toString());
                    selfProbe.put("predeccesor", predecessor == null ? "null" : predecessor.getPort());
                    selfProbe.put("successor", successor == null ? "null" : successor.getPort());
                    selfProbe.put("next", nextSuccessor == null ? "null" : nextSuccessor.getPort());

                    nodes.put(selfProbe);
                    message.put("nodes", nodes);
                    socketQueue.sendMessage(successor, message.toString(), null);
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                    handleUnresponsiveSuccessorNode();
                }
            }
        }
    }

    /**
     * Helper method to send the notify message to some Node that should be made aware of the current Node existence
     * @param rIp - ip of the Node to which notify will be sent
     * @param rPort - port of the Node to which notify will be sent
     * @throws IOException if there was an error during communication with the other node
     */
    public void sendNotify(String rIp, int rPort) throws IOException {
            JSONObject message = new JSONObject();
            message.put("ip", self.getIp());
            message.put("port", self.getPort());
            message.put("type", "notify");

            socketQueue.sendMessage(new Node(rIp, rPort), message.toString(), new OnResponse<String>() {
                @Override
                public String onResponse(String response, Node node) {

                    JSONObject jsonResonse = new JSONObject(response);
                    String status = jsonResonse.getString("status");

                    if (status.equalsIgnoreCase("accept")) {
                        setSuccessor(node);

                        if(jsonResonse.getString("successor").equalsIgnoreCase("null")){
                            setNextSuccessor(null);
                        }else{
                            setNextSuccessor(new Node(jsonResonse.getString("successor")));
                        }
                    } else {
                        // Now what? I think this will be fixed with stabilization
                        //setSuccessor(node);
                        if(!jsonResonse.getString("predecessor").equalsIgnoreCase("null")){
                            Node betterSuccessor = new Node(jsonResonse.getString("predecessor"));
                            try {
                                sendNotify(betterSuccessor.getIp(), betterSuccessor.getPort());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    return null;
                }
            });
    }

    /**
     * This method is called to update the next successor, it requests our current successor to send its own successor
     * to the current Node
     */
    public void updateNextSuccessor() {
        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());
        message.put("type", "request");

        try {

            socketQueue.sendMessage(successor, message.toString(),(response, node) -> {

                JSONObject jsonResonse = new JSONObject(response.toString());
                String successor = jsonResonse.getString("successor");

                if (!successor.equalsIgnoreCase("null")) {
                    Node successorsSuccessor = new Node(successor);
                    setNextSuccessor(successorsSuccessor);
                }
                return null;
            });

        } catch (IOException e) {
            e.printStackTrace(System.err);
            handleUnresponsiveSuccessorNode();
        }

    }

    /**
     * This method is called every time we receive a notify message to either accept or deny the sender as our new
     * predecessor
     * @param n - prospective predecessor
     * @return String representing our response
     */
    public String notifyPredecessor(Node n) {

        JSONObject response = new JSONObject();
        response.put("ip", self.getIp());
        response.put("port", self.getPort());
        response.put("successor", successor == null ? "null": successor.toString());

        if (predecessor == null) {

            // We don't have any predecessor, life is good
            handoverDataOnNewPredecessor(self.getId(), n.getId(), n);
            setPredecessor(n);
            response.put("status", "accept");

            return response.toString();
        } else {

            //This should be our new predecessor
            if (between(n.getId(), predecessor.getId(), self.getId())) {
                handoverDataOnNewPredecessor(predecessor.getId(), n.getId(), n);
                setPredecessor(n);

                response.put("status", "accept");
                return response.toString();
            } else {
                response.put("status", "deny");
                response.put("predecessor", predecessor == null ? "null": predecessor.toString());
                return response.toString();
            }
        }
    }

    /**
     * Method to add a key to either current node storage or to pass it to a node that should store it
     * @param key - a BigInteger that represents the key to be used to access value
     * @param value - a String that should be stored under the key
     */
    public void addKey(BigInteger key, String value) {

        if (predecessor == null || predecessor.equals(self) || between(key,  predecessor.getId(), self.getId())) {
            //System.out.println(self.getId() + " stored " + key + ":" + value); //debug stored
            app.store(key, value);
        } else {

            sendAddToNode(key, value, lookupHelper(key));
        }
    }

    /**
     * This method is called to look up a value by key, either in current storage or to pass the message to a node that
     * should store the value under that key
     * @param key- a BigInteger that represents the key to be used to access value
     * @param asker - node that should receive the result of the look up operation
     */
    public void lookup(BigInteger key, Node asker) {

        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());

        if (predecessor == null || between(key, predecessor.getId(), self.getId())) {
            //do the lookup on this node
            String value = app.get(key);

            message.put("type", "lookup_response");
            message.put("key", key.toString());
            message.put("value", value == null ? "null" : value);

            try {
                socketQueue.sendMessage(asker, message.toString(), null);
            } catch (IOException e) {
                e.printStackTrace(System.err);
                handleUnresponsiveSuccessorNode();
            }

        } else {
            message.put("type", "lookup");
            message.put("key", key.toString());
            message.put("asker", new JSONObject(asker.toString()));

            try {
                socketQueue.sendMessage(lookupHelper(key), message.toString(), null);
            } catch (IOException e) {
                e.printStackTrace(System.err);
                handleUnresponsiveSuccessorNode();
            }
        }
    }

    /**
     * Helper method to determine the closest node to key
     * @param id - BigInteger that represents the key to be found
     * @return Node instance that is closest to the Node that stores the id that we know of
     */
    private Node lookupHelper(BigInteger id) {
        if (fingers.getTable() == null) return successor;

        for (int i = fingers.getTable().size() - 1; i >= 0; i--) {
            Node current = fingers.getTable().get(i);
            Node previous = null;
            if (i == fingers.getTable().size() - 1) {
                previous = fingers.getTable().get(0);
            } else {
                previous = fingers.getTable().get(i + 1);
            }

            if (between(id, previous.getId(), current.getId()))
                return current;
        }
        return successor; // never
    }

    /**
     * Helper method to initiate the lookup operation
     * @param message - message containing the key in question
     */
    public void lookup(String message) {

        JSONObject jsonRequest = new JSONObject(message);

        Node initiator = new Node(jsonRequest.getJSONObject("asker").toString());

        lookup(jsonRequest.getBigInteger("key"), initiator);
    }

    /**
     * Helper method to determine if the key is on the current node
     * @param key - a BigInteger that represents one of the keys
     * @return true if we should store the key, false otherwise
     */
    public boolean isThisOurKey(BigInteger key) {
        if (predecessor == null) return true;
        return key.equals(this.getSelf().getId()) || between(key, predecessor.getId(), self.getId());
    }

    /**
     * Helper method to determine if the key lies between from and to, accounting for ring structure, e.g.
     * in the ring of 1 - 2 - 3 - 1 the node 2 is between 1 and 3, but it is also between 3 and 1
     * @param key - BigInteger representing a key to be placed
     * @param from - BigInteger representing the lower boundary of the interval
     * @param to - BigInteger representing the upper boundary of the interval
     * @return true if key is between from and to, false otherwise
     */
    private boolean between(BigInteger key, BigInteger from, BigInteger to) {
        if (from.compareTo(to) == -1) {
            return (key.compareTo(from) == 1) && (key.compareTo(to) == 0 || key.compareTo(to) == -1);
        } else if (from.compareTo(to) == 1) {
            return (key.compareTo(from) == 1) || (key.compareTo(to) == 0 || key.compareTo(to) == -1);
        } else {
            return true;
        }
    }

    /**
     * Getter method for self
     * @return self
     */
    public Node getSelf() {
        return self;
    }

    /**
     * Helper method that should be called when a value was found by a key
     * @param clientMessage - message that contains key/value pair
     */
    public void deliverLookup(String clientMessage) {
        JSONObject jsonRequest = new JSONObject(clientMessage);
        app.onFound(jsonRequest.getBigInteger("key"), jsonRequest.getString("value"));
    }

    /**
     * Method that should be called every time we receive a finger probe message
     * @param clientMessage - current state of the finger probe message serialized a JSON in a String
     */
    public void handleFingerProbe(String clientMessage) {
        String message = fingers.dealWithFingerProbe(clientMessage, new OnResponse<String>() {
            @Override
            public String onResponse(String response, Node node) {
                try {
                    socketQueue.sendMessage(node, response, null);
                } catch (IOException e) {
                    System.out.println(node.getIp() + ":" + node.getPort() + " was unresponsive"); //todo do we need to do anything else?
                    e.printStackTrace(System.err);
                }

                return null;
            }
        });

        try {
            socketQueue.sendMessage(successor, message, null);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            handleUnresponsiveSuccessorNode();
        }
    }

    /**
     * Method that should be called every time the successer node can not be reached
     */
    private synchronized void handleUnresponsiveSuccessorNode() {
        boolean isRepsonsive = Monitor.isResponsive(successor);

        System.out.println("Was responsive:" + isRepsonsive);

        if(!isRepsonsive){
            System.out.println(successor.toString() + " is not responding to " + self.toString() + "\n" + nextSuccessor);
            if (nextSuccessor != null) {
                setSuccessor(nextSuccessor);
            } else {
                setSuccessor(self);
            }
        }
    }

    /**
     * Method that is called, when the nod ereceives the response to finger probe request
     * @param clientMessage - message that contains the response
     */
    public  void fingerProbeResponse(String clientMessage) {
        fingers.updateTable(clientMessage);
    }

    /**
     * Method that is called, when the current node receives unlink_predecessor message. That message forcefully
     * disconnects the predecessor from the current node and switches the predecessor to the requester
     * @param clientMessage - message with the information about hte requester node
     */
    public  void unlinkPredecessor(String clientMessage) {
        System.out.println("Unlinking");
        JSONObject message = new JSONObject(clientMessage);
        Node newPredecessor = new Node(message.getString("ip"), message.getInt("port"));
        setPredecessor(newPredecessor);
    }

    /**
     * Method that should be called on node exit from DHT in order to pass all stored data to other nodes in DHT
     */
    private void transferStoredData() {
        sendBatchAddToNode(app.split(predecessor.getId(), successor.getId()), successor);
    }

    /**
     * Method that should be called on predecessor change in order to pass stored data to the node that
     * should now store it
     */
    private void handoverDataOnNewPredecessor(BigInteger from, BigInteger to, Node receiver) {
        sendBatchAddToNode(app.split(from, to), receiver);
    }

    /**
     * Helper method to send an add message to a node
     * @param key - BigInteger id that will be the key
     * @param value - String value that should be stored under the key
     * @param to - Node that should receive the add message
     */
    private void sendAddToNode(BigInteger key, String value, Node to) {
        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());
        message.put("type", "add");
        message.put("key", key.toString());
        message.put("value", value);

        try {
            socketQueue.sendMessage(to, message.toString(), null);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            if (to.getId().equals(successor.getId())) {
                handleUnresponsiveSuccessorNode();
                sendAddToNode(key, value, to); //retry
            } else {
                sendAddToNode(key, value, successor);
            }
        }
    }

    /**
     * Helper method to batch add key/value pairs
     * @param batch - batch of key/value pairs to be stored
     * @param to - Node that should receive the add batch message
     */
    private void sendBatchAddToNode(Map<BigInteger, String> batch, Node to) {
        if (batch.size() > 0) {
            JSONObject message = new JSONObject();
            message.put("ip", self.getIp());
            message.put("port", self.getPort());
            message.put("type", "handover");

            JSONArray values = new JSONArray();
            for (Map.Entry<BigInteger, String> entry : batch.entrySet()) {
                JSONObject value = new JSONObject();
                value.put("key", entry.getKey());
                value.put("value", entry.getValue());
                values.put(value);
            }

            message.put("batch", values);

            try {
                socketQueue.sendMessage(to, message.toString(), null);
            } catch (IOException e) {
                e.printStackTrace(System.err);


                /*
                if (to.getId().equals(successor.getId())) {
                    handleUnresponsiveSuccessorNode();
                    //sendBatchAddToNode(batch, successor);
                } else {
                    System.err.println("Error sending batch to " + to);
                    //sendBatchAddToNode(batch, to);
                }
                */
            }
        }
    }

    /**
     * Method that should be called, when a chunk of stored data is passed to be stored on the current node
     * @param clientMessage - message that contains data to be stored
     */
    public void onHandover(String clientMessage) {
        JSONObject message = new JSONObject(clientMessage);
        JSONArray values = message.getJSONArray("batch");
        for (int i = 0; i < values.length(); i++) {
            JSONObject obj = values.getJSONObject(i);
            app.store(obj.getBigInteger("key"), obj.getString("value"));
        }
    }

    /**
     * Method that is called to remove a key/value pair from the storage on the node by key
     * @param key - BigInteger that should be removed along the value that is stored under this key
     */
    public void removeKey(BigInteger key) {

        if (predecessor == null || predecessor.equals(self) || between(key,  predecessor.getId(), self.getId())) {
            //System.out.println(self.getId() + " stored " + key + ":" + value); //debug stored
            app.remove(key);
        } else {
            sendRemoveToNode(key, lookupHelper(key));
        }
    }

    /**
     * Helper method to send a remove message to a node
     * @param key - BigInteger id that will be the key
     * @param node - Node that should receive the remove message
     */
    private void sendRemoveToNode(BigInteger key, Node node) {
        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());
        message.put("type", "remove");
        message.put("key", key.toString());

        try {
            socketQueue.sendMessage(node, message.toString(), null);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            if (node.getId().equals(successor.getId())) {
                handleUnresponsiveSuccessorNode();
              //  sendRemoveToNode(key, node); //retry
            } else {
              //  sendRemoveToNode(key, successor);
            }
        }

    }
}
