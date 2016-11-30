package se.kth.networking.java.first;

import org.json.JSONObject;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;
import se.kth.networking.java.first.network.ClientAcceptor;
import se.kth.networking.java.first.ring.RingHandler;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

/**
 * Created by Nick on 11/2/2016.
 */
public class Server {

    private RingHandler ringHandler;
    private ClientAcceptor acceptor;
    private ApplicationDomain app;


    public Server(ApplicationDomain app, int port) {
        this.app = app;

        //Please fix dynamic ip
        ringHandler = new RingHandler("127.0.0.1", port, app);

        try {
            acceptor = new ClientAcceptor(port, new OnResponse<String>(){
                @Override
                public String onResponse(String response, Node node) {
                    return handleMessage(response, node);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Server(ApplicationDomain app) {
        this(app, selectAFreePort());
    }

    private static int selectAFreePort(){
        ServerSocket socket = null;
        int port = 0;
        try {
            socket = new ServerSocket(0);
            port = socket.getLocalPort();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return port;
    }

    private boolean isValidMessage(String msg){
        return msg != null;
    }

    private String handleMessage(String clientMessage, Node node){

        JSONObject message = null;
        try{
            message = new JSONObject(clientMessage);
        }catch(Exception e){
            System.out.println("Client message: " + clientMessage);
            return null;
        }

        String response = "Bad request";
        if(isValidMessage(clientMessage)){
            switch (message.getString("type")){
                case "notify":
                    response = ringHandler.notifyPredecessor(node);
                    break;
                case "finger_probe":
                    //System.out.println(clientMessage.toString()); TODO debug purposes
                    ringHandler.handleFingerProbe(clientMessage, node);
                    break;
                case "finger_probe_response":
                    ringHandler.fingerProbeResponse(clientMessage, node);
                    break;
                case "probe":
                    ringHandler.handleProbe(clientMessage, node);
                    break;
                case "request":
                    response = ringHandler.onRequest(clientMessage, node);
                    break;
                case "lookup":
                    ringHandler.lookup(clientMessage);
                    break;
                case "lookup_response":
                    ringHandler.deliverLookup(clientMessage);
                    break;
                case "successorChanged":
                    ringHandler.updateNextSuccessor();
                    break;
                case "unlink_predecessor":
                    ringHandler.unlinkPredecessor(clientMessage);
                    break;
                case "add":
                    String payload = message.getString("value");
                    BigInteger key = message.getBigInteger("key");

                    ringHandler.addKey(key, payload);
                    break;
            }
        }

        return response;
    }

    public void addKey(BigInteger key, String value){
        ringHandler.addKey(key, value);
    }

    public void lookup(BigInteger key){
        ringHandler.lookup(key, ringHandler.getSelf());
    }

    public void sendNotify(String ip, int port){
        try {
            ringHandler.sendNotify(ip, port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void probe(){
        ringHandler.probe();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ApplicationDomain app = new ApplicationDomain() {

            HashMap<BigInteger, String> store = new HashMap<>();

            @Override
            public void storeKey(BigInteger key, String value) {
                store.put(key, value);
                System.out.println(key + ":" + value);
            }

            @Override
            public String getKey(BigInteger key) {
                return store.get(key);
            }

            @Override
            public void foundKey(BigInteger key, String value) {
                System.out.println("Key:" + key + ", Value: " + value);
            }

            @Override
            public Map<BigInteger, String> getStore() {
                return store;
            }
        };

        Server server1 = new Server(app, 5050);
        server1.start();

        Server server2 = new Server(app, 6060);
        server2.start();

        Thread.sleep(200);

        Server server3 = new Server(app, 7070);
        server3.start();

        //server1.sendNotify(server2.getRingHandler().getIp(), server2.getRingHandler().getPort());
        server2.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
        Thread.sleep(200);
        server3.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
        Thread.sleep(200);

        server2.addKey(new BigInteger("22"), "gravy");
        server2.addKey(new BigInteger("55"), "stuff");

        //Thread.sleep(3000);

        server2.lookup(new BigInteger("55"));

        server3.probe();

        List<Server> servers = new ArrayList<>();
//        servers.add(server1);
//        servers.add(server2);
//        servers.add(server3);


//        for (int i = 0; i < 3; i++) {
//            Server s = new Server(app);
//            servers.add(s);
//            s.start();
//            Thread.sleep(200);
//            System.out.println("Started: " + i);
//            s.addKey(s.getRingHandler().getSelf().getId().subtract(BigInteger.ONE), "gravy" + i);
//
//
//            Server serlectedParent = servers.get(Helper.getHelper().getRandom(0, servers.size() -1));
//            s.sendNotify(serlectedParent.getRingHandler().getSelf().getIp(), serlectedParent.getRingHandler().getSelf().getPort());
//        }

        Thread.sleep(200);
        server2.stop();

//        server3.probe();
       // Thread.sleep(10000);
        System.out.println("done");

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("run probe");
                server3.probe();


                System.out.println("do lookup");

                server1.lookup(new BigInteger("55"));
                server1.lookup(new BigInteger("22"));
//                for (Server s : servers) {
//                    server1.lookup(s.getRingHandler().getSelf().getId().subtract(BigInteger.ONE));
//                }

//                Server s = new Server(app);
//                s.start();
//                s.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
            }
        };

        //Set a random day so that the stabalizers don't run at the same time
        int interval = 2000;
        int delay = Helper.getHelper().getRandom(10, interval);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, 0, interval);

    }

    private void start() {
        acceptor.start();
    }

    private void stop() {
        acceptor.shutdown();
        ringHandler.shutdown();
    }

    public RingHandler getRingHandler() {
        return ringHandler;
    }
}
