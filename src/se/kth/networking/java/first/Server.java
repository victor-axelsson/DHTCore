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
    private static long start;
    private static long end;


    public Server(ApplicationDomain app, String ip, int port) {
        this.app = app;
        //Default to localhost
        if(ip == null){
            ip = "127.0.0.1";
        }

        //Please fix dynamic ip
        ringHandler = new RingHandler(ip, port, app);

        try {
            acceptor = new ClientAcceptor(port, new OnResponse<String>(){
                @Override
                public String onResponse(String response, Node node) {
                    return handleMessage(response, node);
                }
            });
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    public Server(ApplicationDomain app) {
        this(app, null, selectAFreePort());
    }

    public Server(ApplicationDomain app, String ip) {
        this(app, ip, selectAFreePort());
    }

    private static int selectAFreePort(){
        ServerSocket socket = null;
        int port = 0;
        try {
            socket = new ServerSocket(0);
            port = socket.getLocalPort();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
        return port;
    }

    private boolean isValidMessage(String msg){
        return msg != null;
    }

    private synchronized String handleMessage(String clientMessage, Node node){

        String response = "Bad request";

        System.out.println("RECEIVED: " + clientMessage + " \nSELF:" + getRingHandler().getSelf().getPort());
        JSONObject message = null;
        try{
            message = new JSONObject(clientMessage);
        }catch(Exception e){
            e.printStackTrace(System.err);
            System.err.println("Could not parse message: " + clientMessage);
            return response;
        }

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
                case "handover":
                    ringHandler.onHandover(clientMessage);
                    break;
                case "lookup_response":
                    ringHandler.deliverLookup(clientMessage);
                    break;
                case "unlink_predecessor":
                    ringHandler.unlinkPredecessor(clientMessage);
                    break;
                case "add":
                    String payload = message.getString("value");
                    BigInteger key = message.getBigInteger("key");
                    ringHandler.addKey(key, payload);
                    break;
                case "remove":
                    BigInteger removeKey = message.getBigInteger("key");
                    ringHandler.removeKey(removeKey);
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

            Map<BigInteger, String> store = new HashMap<>();

            @Override
            public void store(BigInteger key, String value) {
                store.put(key, value);
                System.out.println("Stored: Key:" + key + ", Value: " + value);
            }

            @Override
            public String get(BigInteger key) {
                return store.get(key);
            }

            @Override
            public void remove(BigInteger key) {
                store.remove(key);
                System.out.println("Removed: Key:" + key);
            }

            @Override
            public void onFound(BigInteger key, String value) {
                end = System.currentTimeMillis();
//                System.err.println("Time:" + ((end - start) / 1000.0) + " sec");
                System.out.println("Key:" + key + ", Value: " + value);
            }

            @Override
            public Map<BigInteger, String> split(BigInteger from, BigInteger to) {
                Map<BigInteger, String> move = new HashMap<>();
                Map<BigInteger, String> keep = new HashMap<>();

                for (Map.Entry<BigInteger, String> entry : store.entrySet()) {
                    if (entry.getKey().compareTo(from) >= 0 && entry.getKey().compareTo(to) == -1) {
                        keep.put(entry.getKey(), entry.getValue());
                    } else {
                        move.put(entry.getKey(), entry.getValue());
                    }
                }
                store = keep;
                return move;
            }

            @Override
            public Map<BigInteger, String> getStore() {
                return store;
            }
        };


        //Server server1 = new Server(app, "130.229.146.35", 5050);
        Server server1 = new Server(app, null, 5050);
        server1.start();

        Server server2 = new Server(app, null, 6060);
        server2.start();

        Server server3 = new Server(app, null, 7070);
        server3.start();


        Server server4 = new Server(app, null, 7071);
        server4.start();

        Thread.sleep(1000);

        //server1.sendNotify(server2.getRingHandler().getIp(), server2.getRingHandler().getPort());
        server2.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server3.sendNotify(server2.getRingHandler().getSelf().getIp(), server2.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server4.sendNotify(server3.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server1.sendNotify(server4.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        //Thread.sleep(1000);


        BigInteger key = server2.getRingHandler().getSelf().getId().subtract(BigInteger.ONE);
        server2.addKey(key, "gravy");
        //server2.addKey(new BigInteger("55"), "stuff");

        Thread.sleep(10000);
      //  server2.stop();

        List<Server> servers = new ArrayList<>();
        servers.add(server1);
        servers.add(server2);
        servers.add(server3);
        servers.add(server4);
        Thread.sleep(10000);
    //    server4.getRingHandler().removeKey(new BigInteger("55"));


//        for (int i = 0; i < 57; i++) {
//            Server s = new Server(app);
//            servers.add(s);
//            s.start();
//            Thread.sleep(500);
//            System.out.println("Started: " + i);
//            s.addKey(s.getRingHandler().getSelf().getId().subtract(BigInteger.ONE), "gravy" + i);
//
//
//            Server serlectedParent = servers.get(servers.size() - 2);
//            s.sendNotify(serlectedParent.getRingHandler().getSelf().getIp(), serlectedParent.getRingHandler().getSelf().getPort());
//           // serlectedParent.sendNotify(s.getRingHandler().getSelf().getIp(), s.getRingHandler().getSelf().getPort());
//        }

        Thread.sleep(200);
        //server2.stop();

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
//                start = System.currentTimeMillis();
//                servers.get(servers.size() - 1).lookup(key);
//                for (Server s : servers) {
//                    server1.lookup(s.getRingHandler().getSelf().getId().subtract(BigInteger.ONE));
//                }

//                Server s = new Server(app);
//                s.start();
//                s.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
            }
        };

        /*

        server2.stop();

        //Set a random day so that the stabalizers don't run at the same time

        */
        int interval = 3000;
        //int delay = Helper.getHelper().getRandom(10, interval);
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, 2000, interval);

    }

    private void start() {
        acceptor.start();
    }

    private void stop() {
        ringHandler.shutdown();
        acceptor.shutdown();
    }

    public RingHandler getRingHandler() {
        return ringHandler;
    }
}
