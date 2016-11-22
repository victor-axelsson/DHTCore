package se.kth.networking.java.first.ring;


import se.kth.networking.java.first.ApplicationDomain;
import se.kth.networking.java.first.Helper;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;
import se.kth.networking.java.first.network.Client;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by victoraxelsson on 2016-11-04.
 */
public class RingHandler {

    Node predecessor;
    Node successor;
    Node self;
    private int port;
    private String ip;
    ApplicationDomain app;

    public RingHandler(String ip, int port, ApplicationDomain app){
        this.ip = ip;
        this.port = port;
        this.self = new Node(ip, port);
        this.successor = this.self;
        this.app = app;

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                stabilize();
            }
        };

        //Set a random day so that the stabalizers don't run at the same time
        int interval = 3000;
        int delay = Helper.getHelper().getRandom(10, interval);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, delay, interval);
    }

    private void stabilize(){
        try {
            String msg = "request:" + self.getIp() + "," + self.getPort();
            Client c = new Client(successor.getAsSocket(), msg, new OnResponse<String>() {
                @Override
                public String onResponse(String response, Node node) {

                    String[] args = response.split(";");

                    Node otherPredesessor = null;
                    Node otherSuccessor = null;

                    if(!args[0].equalsIgnoreCase("null")){
                        otherPredesessor = new Node(args[0]);
                    }

                    if(!args[1].equalsIgnoreCase("null")){
                        otherSuccessor = new Node(args[1]);
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

    public String onRequest(String clientMessage, Node node){
        if(predecessor == null){
            return "null;" + successor.toString();
        }else{
            return predecessor.toString() + ";" + successor.toString();
        }
    }

    public void onStabilizeRequest(Node otherPredesesor, Node otherSuccessor){

        if(otherPredesesor == null){
            // It have no predecessor, notify our successor that its predecessor might be us
            sendNotify(successor.getIp(), successor.getPort());

        }else if(otherPredesesor.getId() == self.getId()){
            //All is well, its us.

        }else if(otherPredesesor.getId() == successor.getId()){
            //The successors predesessor is itself, we should probably be there instead
            sendNotify(successor.getIp(), successor.getPort());

        }else{
            if(between(otherPredesesor.getId(), self.getId(), successor.getId())){
                // we probably hve the wrong successor
                sendNotify(otherPredesesor.getIp(), otherPredesesor.getPort());
            }else{
                //we should be in between the successor and its predecessor
                sendNotify(successor.getIp(), successor.getPort());
            }
        }

    }

    public void probe(){
        try {
            String msg = "probe:" + self.getIp() + "," + self.getPort() + "," + self.toString();
            Client c = new Client(successor.getAsSocket(), msg, null);
            c.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handleProbe(String clientMessage, Node node){
        String[] parts = clientMessage.split(":");
        String[] args = parts[1].split(",");

        Node initiator = new Node(args[0], Integer.parseInt(args[1]));
        if(initiator.getId() == self.getId()){
            System.out.println("I got it back from the ring, " + clientMessage);
        }else{
            try {
                String msg = clientMessage + "," + self.toString();
                Client c = new Client(successor.getAsSocket(), msg, null);
                c.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendNotify(String rIp, int rPort){
        try {
            String msg = "notify:" + ip + "," + port;
            Client s = new Client(new Socket(rIp, rPort), msg, new OnResponse<String>() {
                @Override
                public String onResponse(String response, Node node) {

                    if(response.equalsIgnoreCase("accept")){
                        successor = node;
                    }else{
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


    public String notifyPredecessor(Node n){
        if(predecessor == null){

            // We don't have any predecessor, life is good
            predecessor = n;
            return "accept";
        }else{

            //This should be our new predecessor
            if(between(n.getId(), predecessor.getId(), self.getId())){
                predecessor = n;
                return "accept";
            }else{
                return "deny";
            }
        }
    }

    public void addKey(int key, String value){
        if(between(key, predecessor.getId(), self.getId())){
            app.storeKey(key, value);
        }else{
            String msg = "add:" + self.getIp() + "," + self.getPort() + "," + key + "," + value;
            Client c = null;
            try {
                c = new Client(successor.getAsSocket(), msg, null);
                c.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void lookup(int key, Node asker){
        if(between(key, predecessor.getId(), self.getId())){
            //do the lookup on this node
            String value = app.getKey(key);
            String msg = "lookup_response:" + self.getIp() + "," + self.getPort() + "," + key + "," + value;
            Client c = null;
            try {
                c = new Client(successor.getAsSocket(), msg, null);
                c.start();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }else{
            String msg = "lookup:" + self.getIp() + "," + self.getPort() + "," + key + "," + asker.toString();
            Client c = null;
            try {
                c = new Client(successor.getAsSocket(), msg, null);
                c.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void lookup(String message){
        //Example of a message:
        //lookup:127.0.0.1,6060,55,{"port":6060,"ip":"127.0.0.1","id":-1109658127}

        String argsPart = message.substring("lookup:".length());
        String[] args = argsPart.split(",");
        int key = Integer.parseInt(args[2]);
        String[] jsonParts = Arrays.copyOfRange(args, 3, args.length);
        Node initiator = new Node(String.join(",", jsonParts));

        lookup(key, initiator);
    }

    private boolean between(int key, int from, int to){
        if(from < to){
            return (key > from) && (key <= to);
        }else if(from > to){
            return (key > from) || (key <= to);
        }else {
            return true;
        }
    }

    public int getPort() {
        return port;
    }

    public String getIp() {
        return ip;
    }

    public Node getSelf() {
        return self;
    }

    public void deliverLookup(String clientMessage) {
        //lookup_response:127.0.0.1,5050,55,["SomeData"],{"port":5050,"ip":"127.0.0.1","id":-1109687949}

        String argsPart = clientMessage.substring("lookup_response:".length());
        String[] args = argsPart.split(",");
        int key = Integer.parseInt(args[2]);
        String[] jsonParts = Arrays.copyOfRange(args, 3, args.length);
        String data = String.join(",", jsonParts);

        //Node keyKeeper = new Node(args[0], Integer.parseInt(args[1]));

        app.foundKey(key, data);

    }
}
