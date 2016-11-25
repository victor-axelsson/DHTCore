package se.kth.networking.java.first.ring;


import org.json.JSONArray;
import org.json.JSONObject;
import se.kth.networking.java.first.ApplicationDomain;
import se.kth.networking.java.first.Helper;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;
import se.kth.networking.java.first.network.Client;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.Arrays;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by victoraxelsson on 2016-11-04.
 */
public class RingHandler {

    Node predecessor;
    Node successor;
    FingerTable fingers;
    Node self;
    ApplicationDomain app;

    public RingHandler(String ip, int port, ApplicationDomain app) {
        this.self = new Node(ip, port);
        this.successor = this.self;
        this.app = app;
        this.fingers = new FingerTable(self, this);

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                stabilize();
            }
        };

        //Set a random day so that the stabalizers don't run at the same time
        int interval = 500;
        int delay = Helper.getHelper().getRandom(10, interval);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, delay, interval);
            TimerTask fingers = new TimerTask() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    updateFingerTable();
                }
            };

            //Set a random day so that the stabalizers don't run at the same time
            int interval2 = 3000;
            int delay2 = Helper.getHelper().getRandom(10, interval);

            Timer timer2 = new Timer();
            timer2.scheduleAtFixedRate(fingers, delay2, interval2);

    }

    private void updateFingerTable() {
        JSONObject message = new JSONObject(fingers.createFingerProbeMessage());
        Client c = null;
        try {
            c = new Client(successor.getAsSocket(), message.toString(), null);
            c.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void stabilize() {
        try {

            JSONObject message = new JSONObject();
            message.put("type", "request");
            message.put("ip", self.getIp());
            message.put("port", self.getPort());

            Client c = new Client(successor.getAsSocket(), message.toString(), new OnResponse<String>() {
                @Override
                public String onResponse(String response, Node node) {

                    JSONObject jsonResponse = new JSONObject(response);

                    String[] args = response.split(";");
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
            c.start();
        } catch (IOException e) {
            e.printStackTrace();
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
            sendNotify(successor.getIp(), successor.getPort());

        } else if (otherPredesesor.getId() == self.getId()) {
            //All is well, its us.

        } else if (otherPredesesor.getId() == successor.getId()) {
            //The successors predesessor is itself, we should probably be there instead
            sendNotify(successor.getIp(), successor.getPort());

        } else {
            if (between(otherPredesesor.getId(), self.getId(), successor.getId())) {
                // we probably hve the wrong successor
                sendNotify(otherPredesesor.getIp(), otherPredesesor.getPort());
            } else {
                //we should be in between the successor and its predecessor
                sendNotify(successor.getIp(), successor.getPort());
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

            // String msg = "probe:" + self.getIp() + "," + self.getPort() + "," + self.toString();
            Client c = new Client(successor.getAsSocket(), message.toString(), null);
            c.start();
        } catch (IOException e) {
            e.printStackTrace();
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

                Client c = new Client(successor.getAsSocket(), message.toString(), null);
                c.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendNotify(String rIp, int rPort) {
        try {

            JSONObject message = new JSONObject();
            message.put("ip", self.getIp());
            message.put("port", self.getPort());
            message.put("type", "notify");

            //String msg = "notify:" + ip + "," + port;

            Client s = new Client(new Socket(rIp, rPort), message.toString(), new OnResponse<String>() {
                @Override
                public String onResponse(String response, Node node) {

                    JSONObject jsonResonse = new JSONObject(response);
                    String status = jsonResonse.getString("status");

                    if (status.equalsIgnoreCase("accept")) {
                        successor = node;
                    } else {
                        // Now what? I think this will be fixed with stabilization
                        successor = node;
                    }

                    return null;
                }
            });
            s.start();
        } catch (IOException e) {
            e.printStackTrace();
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
                return response.toString();
            }
        }
    }

    public void addKey(BigInteger key, String value) {
        if (between(key, predecessor.getId(), self.getId())) {
            app.storeKey(key, value);
        } else {

            JSONObject message = new JSONObject();
            message.put("ip", self.getIp());
            message.put("port", self.getPort());
            message.put("type", "add");
            message.put("key", key);
            message.put("value", value);

            //String msg = "add:" + self.getIp() + "," + self.getPort() + "," + key + "," + value;
            Client c = null;
            try {
                c = new Client(successor.getAsSocket(), message.toString(), null);
                c.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void lookup(BigInteger key, Node asker) {

        JSONObject message = new JSONObject();
        message.put("ip", self.getIp());
        message.put("port", self.getPort());

        if (between(key, predecessor.getId(), self.getId())) {
            //do the lookup on this node
            String value = app.getKey(key);

            message.put("type", "lookup_response");
            message.put("key", key);
            message.put("value", value);

            //String msg = "lookup_response:" + self.getIp() + "," + self.getPort() + "," + key + "," + value;
            Client c = null;
            try {
                c = new Client(successor.getAsSocket(), message.toString(), null);
                c.start();
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {

            message.put("type", "lookup");
            message.put("key", key);
            message.put("asker", new JSONObject(asker.toString()));

            //String msg = "lookup:" + self.getIp() + "," + self.getPort() + "," + key + "," + asker.toString();
            Client c = null;
            try {
                c = new Client(successor.getAsSocket(), message.toString(), null);
                c.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
                Client c = null;
                try {
                    c = new Client(node.getAsSocket(), response, null);
                    c.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return null;
            }
        });

        Client c = null;
        try {
            c = new Client(successor.getAsSocket(), message, null);
            c.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void fingerProbeResponse(String clientMessage, Node node) {
        fingers.updateTable(clientMessage);
    }
}
