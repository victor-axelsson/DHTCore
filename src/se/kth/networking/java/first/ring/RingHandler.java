package se.kth.networking.java.first.ring;


import org.json.JSONArray;
import org.json.JSONObject;
import se.kth.networking.java.first.ApplicationDomain;
import se.kth.networking.java.first.Helper;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

/**
 * Created by victoraxelsson on 2016-11-04.
 */
public class RingHandler {

    Node predecessor;
    Node successor;
    Node nextSuccessor;
    FingerTable fingers;
    Node self;
    ApplicationDomain app;
    Timer stabilizeTimer;
    Timer fingerTimer;
    SocketQueue socketQueue;

    public RingHandler(String ip, int port, ApplicationDomain app) {
        this.self = new Node(ip, port);
        this.successor = this.self;
        this.app = app;
        this.fingers = new FingerTable(self, this);
        socketQueue = new SocketQueue();

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
        int delay2 = Helper.getHelper().getRandom(10, interval2);

        fingerTimer = new Timer();

        fingerTimer.scheduleAtFixedRate(fingers, delay2, interval2);
    }

    public void shutdown() {
        stabilizeTimer.cancel();
        stabilizeTimer.purge();
        fingerTimer.cancel();
        fingerTimer.purge();
        transferStoredData();
    }

    private void transferStoredData() {
        for (Map.Entry<BigInteger, String> entry : app.getStore().entrySet()) {
            sendKeyToSuccessor(entry.getKey(), entry.getValue());
        }
    }

    private void updateFingerTable() {
        if(!successor.getId().equals(self.getId()) && predecessor != null &&
                !predecessor.getId().equals(self.getId())){

            JSONObject message = new JSONObject(fingers.createFingerProbeMessage());
            try {
                socketQueue.sendMessage(successor, message.toString(), null);
            } catch (IOException e) {
                handleUnresponsiveSuccessorNode(successor);
            }
        }

    }

    private void stabilize() {

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

                    onStabilizeRequest(otherPredesessor, otherSuccessor);
                    return null;
                }
            });
        } catch (IOException e) {
            System.out.println("Caught exception in stabilize " + e.getMessage());
            handleUnresponsiveSuccessorNode(successor);
        }

    }

    public String onRequest(String clientMessage, Node node) {
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

    public void onStabilizeRequest(Node otherPredesesor, Node otherSuccessor) {

        if (otherPredesesor == null) {

            // It have no predecessor, notify our successor that its predecessor might be us
            try {
                sendNotify(successor.getIp(), successor.getPort());
            } catch (IOException e) {
                handleUnresponsiveSuccessorNode(successor);
            }

        } else if(Objects.equals(otherPredesesor.getId(), self.getId())) {
            //stuff
        } else if (Objects.equals(otherPredesesor.getId(), successor.getId())) {
            //The successors predesessor is itself, we should probably be there instead
            try {
                sendNotify(successor.getIp(), successor.getPort());
            } catch (IOException e) {
                handleUnresponsiveSuccessorNode(successor);
            }

        } else{
            if (between(otherPredesesor.getId(), self.getId(), successor.getId())) {

                // we probably have the wrong successor
                try {
                    sendNotify(otherPredesesor.getIp(), otherPredesesor.getPort());

                    nextSuccessor = successor;

                    if(otherPredesesor == null){
                        System.out.println("Was null");
                    }
                    successor = otherPredesesor;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                //we should be in between the successor and its predecessor
                try {
                    sendNotify(successor.getIp(), successor.getPort());
                } catch (IOException e) {
                    handleUnresponsiveSuccessorNode(successor);
                }
            }
        }
    }

    public void probe() {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "probe");
            JSONArray nodes = new JSONArray();
            nodes.put(new JSONObject(self.toString()));
            message.put("nodes", nodes);

            System.out.println(successor);

            socketQueue.sendMessage(successor, message.toString(), null );

        } catch (IOException e) { //if exception -> node died
            System.out.println("Caught exception in probe");
            handleUnresponsiveSuccessorNode(successor);
        }
    }

    public void handleProbe(String clientMessage, Node node) {
        JSONObject message = new JSONObject(clientMessage);

        Node initiator = new Node(message.getJSONArray("nodes").get(0).toString());

        //Node initiator = new Node(args[0], Integer.parseInt(args[1]));
        if (Objects.equals(initiator.getId(), self.getId())) {
            System.out.println("I got it back from the ring, " + clientMessage);
        } else {
            try {
                message.put("nodes", message.getJSONArray("nodes").put(new JSONObject(self.toString())));
                socketQueue.sendMessage(successor, message.toString(), null);
            } catch (IOException e) {
                System.out.println("Caught exception in handleprobe");
                handleUnresponsiveSuccessorNode(successor);
            }
        }
    }

    public void sendNotify(String rIp, int rPort) throws IOException {
            JSONObject message = new JSONObject();
            message.put("ip", self.getIp());
            message.put("port", self.getPort());
            message.put("type", "notify");

        System.out.println("---");
        System.out.println(rIp + ":" + rPort);
        System.out.println(message);
        System.out.println("---");

            socketQueue.sendMessage(new Node(rIp, rPort), message.toString(), new OnResponse<String>() {
                @Override
                public String onResponse(String response, Node node) {

                    JSONObject jsonResonse = new JSONObject(response);
                    String status = jsonResonse.getString("status");

                    if (status.equalsIgnoreCase("accept")) {
                        successor = node;
                    }
                    /*
                    else{
                        Node betterSuccessor = new Node(jsonResonse.optJSONObject("better_successor").toString());
                        successor = betterSuccessor;
                    }
                    */

                    /*
                    else {
                        // Now what? I think this will be fixed with stabilization
                        successor = node;
                    }
                    */


                    updateNextSuccessor();
                    if (predecessor != null)
                        notifyPredecessorOfNewSuccessor();


                    return null;
                }
            });


    }

    private void notifyPredecessorOfNewSuccessor() {
        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());
        message.put("type", "successorChanged");

        try {
            socketQueue.sendMessage(predecessor, message.toString(), null);
        } catch (IOException e) {
            handleUnresponsivePredecessorNode();
        }
    }

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
                    nextSuccessor = new Node(successor);
                }
                return null;
            });

        } catch (IOException e) {
            handleUnresponsiveSuccessorNode(successor);
        }

    }

    public String notifyPredecessor(Node n) {

        JSONObject response = new JSONObject();
        response.put("ip", self.getIp());
        response.put("port", self.getPort());

        if (predecessor == null) {

            // We don't have any predecessor, life is good
            predecessor = n;
            response.put("status", "accept");

            return response.toString();
        } else {

            //This should be our new predecessor
            if (between(n.getId(), predecessor.getId(), self.getId())) {
                predecessor = n;

                response.put("status", "accept");
                return response.toString();
            } else {
                response.put("status", "deny");
                response.put("better_successor", new JSONObject(predecessor.toString()));

                return response.toString();
            }
        }
    }

    public void addKey(BigInteger key, String value) {

        if (predecessor == null || between(key,  predecessor.getId(), self.getId())) {
            //System.out.println(self.getId() + " stored " + key + ":" + value); //debug stored
            app.storeKey(key, value);
        } else {

            System.out.println("Was not null" + predecessor);
            sendKeyToSuccessor(key, value);
        }
    }

    private void sendKeyToSuccessor(BigInteger key, String value) {
        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());
        message.put("type", "add");
        message.put("key", key);
        message.put("value", value);

        try {
            socketQueue.sendMessage(successor, message.toString(), null);
        } catch (IOException e) {
            handleUnresponsiveSuccessorNode(successor);
            sendKeyToSuccessor(key, value); //retry
        }
    }

    public void lookup(BigInteger key, Node asker) {

        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());

        if (predecessor == null || between(key, predecessor.getId(), self.getId())) {
            //do the lookup on this node
            String value = app.getKey(key);

            message.put("type", "lookup_response");
            message.put("key", key);
            message.put("value", value);

            try {
                socketQueue.sendMessage(successor, message.toString(), null);
            } catch (IOException e) {
                handleUnresponsiveSuccessorNode(successor);
            }

        } else {
            message.put("type", "lookup");
            message.put("key", key);
            message.put("asker", new JSONObject(asker.toString()));

            try {
                socketQueue.sendMessage(lookupHelper(key), message.toString(), null);
            } catch (IOException e) {
                handleUnresponsiveSuccessorNode(successor);
            }
        }
    }

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

    public void lookup(String message) {

        JSONObject jsonRequest = new JSONObject(message);

        Node initiator = new Node(jsonRequest.getJSONObject("asker").toString());

        lookup(jsonRequest.getBigInteger("key"), initiator);
    }

    public boolean isThisOurKey(BigInteger key) {
        if (predecessor == null) return true;
        return key.equals(this.getSelf().getId()) || between(key, predecessor.getId(), self.getId());
    }

    private boolean between(BigInteger key, BigInteger from, BigInteger to) {
        if (from.compareTo(to) == -1) {
            return (key.compareTo(from) == 1) && (key.compareTo(to) == 0 || key.compareTo(to) == -1);
        } else if (from.compareTo(to) == 1) {
            return (key.compareTo(from) == 1) || (key.compareTo(to) == 0 || key.compareTo(to) == -1);
        } else {
            return true;
        }
    }

    public Node getSelf() {
        return self;
    }

    public void deliverLookup(String clientMessage) {
        JSONObject jsonRequest = new JSONObject(clientMessage);
        app.foundKey(jsonRequest.getBigInteger("key"), jsonRequest.getString("value"));

    }


    public void handleFingerProbe(String clientMessage, Node node) {
        String message = fingers.dealWithFingerProbe(clientMessage, new OnResponse<String>() {
            @Override
            public String onResponse(String response, Node node) {
                try {
                    socketQueue.sendMessage(node, response, null);
                } catch (IOException e) {
                    System.out.println(node.getIp() + ":" + node.getPort() + " was unresponsive"); //todo do we need to do anything else?
                }

                return null;
            }
        });

        try {
            socketQueue.sendMessage(successor, message, null);
        } catch (IOException e) {
            handleUnresponsiveSuccessorNode(successor);
        }
    }

    private synchronized void handleUnresponsiveSuccessorNode(Node unresponsive) {

        boolean isRepsonsive = false;
        try {
            successor.getAsSocket();
            isRepsonsive = true;
        } catch (IOException e) {
            isRepsonsive = false;
        }

        System.out.println("Was responsive:" + isRepsonsive);

        if(!isRepsonsive){
            System.out.println(successor.toString() + " is not responding to " + self.toString());
            if (nextSuccessor != null) {

                unlinkPredecessor(nextSuccessor, successor);

                if(nextSuccessor == null){
                    System.out.println("was null");
                }
                successor = nextSuccessor;
                nextSuccessor = null;
                //updateNextSuccessor();
            } else {
                successor = self; //TODO drop probe?
            }
        }
    }

    private void unlinkPredecessor(Node nextSuccessor, Node successor) {

        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());
        message.put("type", "unlink_predecessor");
        message.put("predecessor", successor);

        try {
            socketQueue.sendMessage(nextSuccessor, message.toString(), null);
        } catch (IOException e) {
            System.out.println("Well, fuck. We are linked out. Find some node in the finger table and stabilize");
        }
    }



    private void handleUnresponsivePredecessorNode() {
        System.out.println(predecessor.toString() + " is not responding to " + self.toString());
        predecessor = self;
    }

    public  void fingerProbeResponse(String clientMessage, Node node) {
        fingers.updateTable(clientMessage);
    }

    public  void unlinkPredecessor(String clientMessage) {
        System.out.println("Unlinking");
        JSONObject message = new JSONObject(clientMessage);
        Node newPredecessor = new Node(message.getString("ip"), message.getInt("port"));
        predecessor = newPredecessor;
    }


}
