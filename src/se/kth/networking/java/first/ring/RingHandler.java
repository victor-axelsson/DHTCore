package se.kth.networking.java.first.ring;


import org.json.JSONArray;
import org.json.JSONObject;
import se.kth.networking.java.first.ApplicationDomain;
import se.kth.networking.java.first.Helper;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;
import se.kth.networking.java.first.monitor.Monitor;
import se.kth.networking.java.first.monitor.MonitorModel;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
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
    Monitor monitor;

    public void setSuccessor(Node successor) {
        this.successor = successor;
    }

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

    public void shutdown() {
        stabilizeTimer.cancel();
        stabilizeTimer.purge();
        monitor.stop();
        fingerTimer.cancel();
        fingerTimer.purge();
        transferStoredData();
    }


    private void updateFingerTable() {
        if(!successor.getId().equals(self.getId()) && predecessor != null &&
                !predecessor.getId().equals(self.getId())){

            JSONObject message = new JSONObject(fingers.createFingerProbeMessage());

            try {
                socketQueue.sendMessage(successor, this.getSelf(), message.toString(), null);
            } catch (IOException e) {
                e.printStackTrace(System.err);
                handleUnresponsiveSuccessorNode();
            }
        }

    }

    private void stabilize() {
        if (self.equals(successor)) return;

        JSONObject message = new JSONObject();
        message.put("type", "request");
        message.put("ip", self.getIp());
        message.put("port", self.getPort());

        try {
            socketQueue.sendMessage(successor, this.getSelf(), message.toString(), new OnResponse<String>() {
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

    public void onStabilizeRequest(Node otherPredesesor, Node otherSuccessor, Node other) {

        if (otherPredesesor == null) {

            // It have no predecessor, notify our successor that its predecessor might be us
            try {
                sendNotify(other.getIp(), other.getPort());
            } catch (IOException e) {
                e.printStackTrace(System.err);
                handleUnresponsiveSuccessorNode();
            }

        } else if (Objects.equals(otherPredesesor.getId(), self.getId())) {
            //All is well, its us.
            System.out.println("it us");

        } else if (Objects.equals(otherPredesesor.getId(), other.getId())) {
            //The successors predesessor is itself, we should probably be there instead
            try {
                sendNotify(other.getIp(), other.getPort());
            } catch (IOException e) {
                e.printStackTrace(System.err);
                handleUnresponsiveSuccessorNode();
            }

        } else {
            if (between(otherPredesesor.getId(), self.getId(), other.getId())) {

                // we probably hve the wrong successor
                try {
                    sendNotify(otherPredesesor.getIp(), otherPredesesor.getPort());
                    setSuccessor(otherPredesesor);
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

    public void probe() {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "probe");
            JSONArray nodes = new JSONArray();
            nodes.put(new JSONObject(self.toString()));
            message.put("predeccesor", predecessor == null ? "null" : predecessor.getPort());
            message.put("successor", successor == null ? "null" : successor.getPort());
            message.put("next", nextSuccessor == null ? "null" : nextSuccessor.getPort());
            message.put("nodes", nodes);

            socketQueue.sendMessage(successor, this.getSelf(), message.toString(), null );

        } catch (IOException e) { //if exception -> node died
            e.printStackTrace(System.err);
            handleUnresponsiveSuccessorNode();
        }
    }

    public void handleProbe(String clientMessage, Node node) {
        JSONObject message = new JSONObject(clientMessage);

        JSONArray nodes = message.getJSONArray("nodes");
        if (nodes.length() > 0) {
            String json = nodes.get(0).toString();
            Node initiator = new Node(json);

            //Node initiator = new Node(args[0], Integer.parseInt(args[1]));
            if (self.equals(initiator)) {
                System.out.println("I got it back from the ring, " + clientMessage);
            } else {
                try {
                    JSONObject selfProbe = new JSONObject(self.toString());
                    selfProbe.put("predeccesor", predecessor == null ? "null" : predecessor.getPort());
                    selfProbe.put("successor", successor == null ? "null" : successor.getPort());
                    selfProbe.put("next", nextSuccessor == null ? "null" : nextSuccessor.getPort());

                    message.put("nodes",nodes.put(selfProbe));
                    socketQueue.sendMessage(successor, this.getSelf(), message.toString(), null);
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                    handleUnresponsiveSuccessorNode();
                }
            }
        }
    }

    public void sendNotify(String rIp, int rPort) throws IOException {
            JSONObject message = new JSONObject();
            message.put("ip", self.getIp());
            message.put("port", self.getPort());
            message.put("type", "notify");

            socketQueue.sendMessage(new Node(rIp, rPort), this.getSelf(), message.toString(), new OnResponse<String>() {
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

    public void updateNextSuccessor() {
        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());
        message.put("type", "request");

        try {

            socketQueue.sendMessage(successor, this.getSelf(), message.toString(),(response, node) -> {

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

    public void addKey(BigInteger key, String value) {

        if (predecessor == null || predecessor.equals(self) || between(key,  predecessor.getId(), self.getId())) {
            //System.out.println(self.getId() + " stored " + key + ":" + value); //debug stored
            app.store(key, value);
        } else {
            sendAddToNode(key, value, lookupHelper(key));
        }
    }

    public void lookup(BigInteger key, Node asker) {

        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());

        if (predecessor == null || between(key, predecessor.getId(), self.getId())) {
            //do the lookup on this node
            String value = app.get(key);

            message.put("type", "lookup_response");
            message.put("key", key);
            message.put("value", value == null ? "null" : value);

            try {
                socketQueue.sendMessage(asker, this.getSelf(), message.toString(), null);
            } catch (IOException e) {
                e.printStackTrace(System.err);
                handleUnresponsiveSuccessorNode();
            }

        } else {
            message.put("type", "lookup");
            message.put("key", key);
            message.put("asker", new JSONObject(asker.toString()));

            try {
                socketQueue.sendMessage(lookupHelper(key), this.getSelf(), message.toString(), null);
            } catch (IOException e) {
                e.printStackTrace(System.err);
                handleUnresponsiveSuccessorNode();
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
        app.onFound(jsonRequest.getBigInteger("key"), jsonRequest.getString("value"));
    }


    public void handleFingerProbe(String clientMessage, Node node) {
        String message = fingers.dealWithFingerProbe(clientMessage, new OnResponse<String>() {
            @Override
            public String onResponse(String response, Node node) {
                try {
                    socketQueue.sendMessage(node, getSelf(), response, null);
                } catch (IOException e) {
                    System.out.println(node.getIp() + ":" + node.getPort() + " was unresponsive"); //todo do we need to do anything else?
                    e.printStackTrace(System.err);
                }

                return null;
            }
        });

        try {
            socketQueue.sendMessage(successor, this.getSelf(), message, null);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            handleUnresponsiveSuccessorNode();
        }
    }

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

    public  void fingerProbeResponse(String clientMessage, Node node) {
        fingers.updateTable(clientMessage);
    }

    public  void unlinkPredecessor(String clientMessage) {
        System.out.println("Unlinking");
        JSONObject message = new JSONObject(clientMessage);
        Node newPredecessor = new Node(message.getString("ip"), message.getInt("port"));
        setPredecessor(newPredecessor);
    }

    private void transferStoredData() {
        sendBatchAddToNode(app.split(predecessor.getId(), successor.getId()), successor);
    }

    private void handoverDataOnNewPredecessor(BigInteger from, BigInteger to, Node receiver) {
        sendBatchAddToNode(app.split(from, to), receiver);
    }

    private void sendAddToNode(BigInteger key, String value, Node to) {
        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());
        message.put("type", "add");
        message.put("key", key);
        message.put("value", value);

        try {
            socketQueue.sendMessage(to, this.getSelf(), message.toString(), null);
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
                socketQueue.sendMessage(to, this.getSelf(), message.toString(), null);
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

    public void onHandover(String clientMessage) {
        JSONObject message = new JSONObject(clientMessage);
        JSONArray values = message.getJSONArray("batch");
        for (int i = 0; i < values.length(); i++) {
            JSONObject obj = values.getJSONObject(i);
            app.store(obj.getBigInteger("key"), obj.getString("value"));
        }
    }

    public void removeKey(BigInteger key) {

        if (predecessor == null || predecessor.equals(self) || between(key,  predecessor.getId(), self.getId())) {
            //System.out.println(self.getId() + " stored " + key + ":" + value); //debug stored
            app.remove(key);
        } else {
            sendRemoveToNode(key, lookupHelper(key));
        }
    }

    private void sendRemoveToNode(BigInteger key, Node node) {
        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());
        message.put("type", "remove");
        message.put("key", key);

        try {
            socketQueue.sendMessage(node, this.getSelf(), message.toString(), null);
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
