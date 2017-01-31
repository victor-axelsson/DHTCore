package se.kth.networking.java.first;

import org.json.JSONObject;
import se.kth.networking.java.first.logger.Logger;
import se.kth.networking.java.first.logger.SocketIOLogger;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnAsyncResponse;
import se.kth.networking.java.first.models.OnResponse;
import se.kth.networking.java.first.network.ClientAcceptor;
import se.kth.networking.java.first.ring.RingHandler;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
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


    /**
     * Constructor for a Server instance
     * @param app - implementation of ApplicationDomain interface
     * @param ip - ip of the Server
     * @param port - port of the Server
     */
    public Server(ApplicationDomain app, String ip, int port) {
        this.app = app;
        //Default to localhost
        if(ip == null){
            ip = "127.0.0.1";
        }

        //Please fix dynamic ip
        ringHandler = new RingHandler(ip, port, app);

        try {
            /*
            acceptor = new ClientAcceptor(port, new OnResponse<String>(){
                @Override
                public String onResponse(String response, Node node) {
                    return handleMessage(response, node);
                }
            });
            */

            acceptor = new ClientAcceptor(port, new OnAsyncResponse<String>() {
                @Override
                public void onResponse(String response, Node node, OnResponse<String> onResponse) {
                    handleMessage(response, node, onResponse);
                }
            });

        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    /**
     * Constructor for a Server instance that runs on localhost and automatically selects a free port to use
     * @param app - implementation of ApplicationDomain interface
     */
    public Server(ApplicationDomain app) {
        this(app, null, selectAFreePort());
    }

    /**
     * Constructor for a Server instance that runs on the ip address and automatically selects a free port to use
     * @param app - implementation of ApplicationDomain interface
     * @param ip - ip of the Server
     */
    public Server(ApplicationDomain app, String ip) {
        this(app, ip, selectAFreePort());
    }

    /**
     * Helper method to select a free port on the current machine
     * @return integer calue representing a free port to use
     */
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

    /**
     * Validation of the message
     * @param msg - message to be validated
     * @return true if message is valid, false otherwise
     */
    private boolean isValidMessage(String msg){
        return msg != null;
    }

    /**
     * Main method in server that is used to handle incoming messages
     * @param clientMessage - incoming message from another node
     * @param node - node that has sent the message
     * @return response to the message
     */
    private synchronized void handleMessage(String clientMessage, Node node, OnResponse<String> acker){

        String response = "Bad request";

        System.out.println("RECEIVED: " + clientMessage + " \nSELF:" + getRingHandler().getSelf().getPort());
        JSONObject message = null;
        try{
            message = new JSONObject(clientMessage);
        }catch(Exception e){
            e.printStackTrace(System.err);
            System.err.println("Could not parse message: " + clientMessage);
            acker.onResponse(response, getRingHandler().getSelf());
        }

        if(isValidMessage(clientMessage)){
            switch (message.getString("type")){
                case "notify":
                    response = ringHandler.notifyPredecessor(node);
                    acker.onResponse(response, getRingHandler().getSelf());
                    break;
                case "finger_probe":
                    //System.out.println(clientMessage.toString()); TODO debug purposes
                    acker.onResponse(response, getRingHandler().getSelf());
                    ringHandler.handleFingerProbe(clientMessage);
                    break;
                case "finger_probe_response":
                    acker.onResponse(response, getRingHandler().getSelf());
                    ringHandler.fingerProbeResponse(clientMessage);
                    break;
                case "probe":
                    acker.onResponse(response, getRingHandler().getSelf());
                    ringHandler.handleProbe(clientMessage);
                    break;
                case "request":
                    response = ringHandler.onRequest();
                    acker.onResponse(response, getRingHandler().getSelf());
                    break;
                case "lookup":
                    acker.onResponse(response, getRingHandler().getSelf());
                    ringHandler.lookup(clientMessage);
                    break;
                case "handover":
                    acker.onResponse(response, getRingHandler().getSelf());
                    ringHandler.onHandover(clientMessage);
                    break;
                case "lookup_response":
                    acker.onResponse(response, getRingHandler().getSelf());
                    ringHandler.deliverLookup(clientMessage);
                    break;
                case "unlink_predecessor":
                    acker.onResponse(response, getRingHandler().getSelf());
                    ringHandler.unlinkPredecessor(clientMessage);
                    break;
                case "add":
                    String payload = message.getString("value");
                    BigInteger key = message.getBigInteger("key");
                    //acker.onResponse(response, getRingHandler().getSelf());
                    ringHandler.addKey(key, payload);
                    break;
                case "remove":
                    BigInteger removeKey = message.getBigInteger("key");
                    acker.onResponse(response, getRingHandler().getSelf());
                    ringHandler.removeKey(removeKey);
                    break;
            }
        }
    }

    /**
     * Helper method to add a key on the current server
     * @param key - a BigInteger that represents the key to be used to access value
     * @param value - a String that should be stored under the key
     */
    public void addKey(BigInteger key, String value){
        ringHandler.addKey(key, value);
    }

    /**
     * Helper method to initiate a lookup from the current server
     * @param key - a BigInteger that represents the key to be used to access value
     */
    public void lookup(BigInteger key){
        ringHandler.lookup(key, ringHandler.getSelf());
    }

    /**
     * Helper method to send notify from the current server
     * @param ip - ip of the Node to which notify will be sent
     * @param port - port of the Node to which notify will be sent
     */
    public void sendNotify(String ip, int port){
        try {
            ringHandler.sendNotify(ip, port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method to initiate a probe from the current server
     */
    public void probe(){
        ringHandler.probe();
    }

    /**
     * Method for testing purposes
     * @param args
     * @throws IOException
     * @throws InterruptedException
     */
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
        };
        Logger logger = SocketIOLogger.getLogger();
        logger.log("herecomesdatboi", "waddup"); //test

        String ip = InetAddress.getLocalHost().getHostAddress();//"192.168.1.1";
        try {
            //fourNodesNoAddNoFailure(app, ip);
            //fourNodesNoAddWithFailure(app, ip);
            fourNodesWithAddNoFailure(app, ip);
            //fourNodesWithAddWithFailure(app, ip);
            //nNodesWithAdd(app, ip, 8);  //Here are some problems, was not able to find why
            //nNodesNoAdd(app, ip, 15);
            //nNodesNoAddWithFailure(app, ip, 4); //test does not work
        } finally {
            //SocketIOLogger.getLogger().stop();
        }

    }

    /**
     * Starts the Server instance
     */
    public void start() {
        acceptor.start();
    }

    /**
     * This method cleanly terminates the ClientAcceptor and frees the resources
     */
    public void stop() {
        ringHandler.shutdown();
        acceptor.shutdown();
        SocketIOLogger.getLogger().stop();
    }

    /**
     * Getter for ringHandler
     * @return ringHandler
     */
    public RingHandler getRingHandler() {
        return ringHandler;
    }

    private static void fourNodesNoAddNoFailure(ApplicationDomain app, String ip) throws InterruptedException {
        Server server1 = new Server(app, ip, 5050);
        server1.start();

        Server server2 = new Server(app, ip, 6060);
        server2.start();

        Server server3 = new Server(app, ip, 7070);
        server3.start();

        Server server4 = new Server(app, ip, 7071);
        server4.start();

        Thread.sleep(1000);

        server2.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server3.sendNotify(server2.getRingHandler().getSelf().getIp(), server2.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server4.sendNotify(server3.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server1.sendNotify(server4.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);

        System.out.println("done");

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("run probe");
                server3.probe();
            }
        };

        int interval = 3000;
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, 2000, interval);
    }

    private static void fourNodesWithAddNoFailure(ApplicationDomain app, String ip) throws InterruptedException {
        Server server1 = new Server(app, ip, 5050);
        server1.start();

        Server server2 = new Server(app, ip, 6060);
        server2.start();

        Server server3 = new Server(app, ip, 7070);
        server3.start();

        Server server4 = new Server(app, ip, 7071);
        server4.start();

        Thread.sleep(1000);

        server2.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server3.sendNotify(server2.getRingHandler().getSelf().getIp(), server2.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server4.sendNotify(server3.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server1.sendNotify(server4.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);

        List<Server> servers = new ArrayList<>();
        servers.add(server1);
        servers.add(server2);
        servers.add(server3);
        servers.add(server4);


        System.out.println("done");

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("run probe");
                server3.probe();
//                System.out.println("do lookup");
//                server1.lookup(key);
            }
        };

        int interval = 3000;
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, 2000, interval);


        Thread.sleep(3000);
        BigInteger key = server2.getRingHandler().getSelf().getId().subtract(BigInteger.ONE);
        server3.addKey(key, "gravy");
    }

    private static void fourNodesNoAddWithFailure(ApplicationDomain app, String ip) throws InterruptedException {
        Server server1 = new Server(app, ip, 5050);
        server1.start();

        Server server2 = new Server(app, ip, 6060);
        server2.start();

        Server server3 = new Server(app, ip, 7070);
        server3.start();

        Server server4 = new Server(app, ip, 7071);
        server4.start();

        Thread.sleep(1000);

        server2.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server3.sendNotify(server2.getRingHandler().getSelf().getIp(), server2.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server4.sendNotify(server3.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server1.sendNotify(server4.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("run probe");
                server3.probe();
            }
        };

        int interval = 3000;
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, 2000, interval);

        List<Server> servers = new ArrayList<>();
        servers.add(server1);
        servers.add(server2);
        servers.add(server3);
        servers.add(server4);

        System.out.println("done");
        Thread.sleep(10000);
        server2.stop();
        System.out.println("node stopped");


    }

    private static void fourNodesWithAddWithFailure(ApplicationDomain app, String ip) throws InterruptedException {
        Server server1 = new Server(app, ip, 5050);
        server1.start();

        Server server2 = new Server(app, ip, 6060);
        server2.start();

        Server server3 = new Server(app, ip, 7070);
        server3.start();

        Server server4 = new Server(app, ip, 7071);
        server4.start();

        Thread.sleep(1000);

        server2.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server3.sendNotify(server2.getRingHandler().getSelf().getIp(), server2.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server4.sendNotify(server3.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server1.sendNotify(server4.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);

        List<Server> servers = new ArrayList<>();
        servers.add(server1);
        servers.add(server2);
        servers.add(server3);
        servers.add(server4);

        Thread.sleep(200);
        BigInteger key = server2.getRingHandler().getSelf().getId().subtract(BigInteger.ONE);
        server3.addKey(key, "gravy");

        System.out.println("done");
        Thread.sleep(5000);
        server2.stop();
        System.out.println("node stopped");


        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("run probe");
                server3.probe();
                System.out.println("do lookup");
                server1.lookup(key);
            }
        };

        int interval = 3000;
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, 2000, interval);
    }

    private static void nNodesWithAdd(ApplicationDomain app, String ip, int n) throws InterruptedException {
        Server server1 = new Server(app, ip, 5050);
        server1.start();

        Server server2 = new Server(app, ip, 6060);
        server2.start();

        Server server3 = new Server(app, ip, 7070);
        server3.start();


        Server server4 = new Server(app, ip, 7071);
        server4.start();

        Thread.sleep(1000);

        server2.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server3.sendNotify(server2.getRingHandler().getSelf().getIp(), server2.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server4.sendNotify(server3.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server1.sendNotify(server4.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);

        List<Server> servers = new ArrayList<>();
        servers.add(server1);
        servers.add(server2);
        servers.add(server3);
        servers.add(server4);
        Thread.sleep(10000);

        for (int i = 0; i < n - 4; i++) {
            Server s = new Server(app);
            servers.add(s);
            s.start();
            Thread.sleep(500);
            System.out.println("Started: " + i);
            s.addKey(s.getRingHandler().getSelf().getId().subtract(BigInteger.ONE), "gravy" + i);

            Server serlectedParent = servers.get(servers.size() - 2);
            s.sendNotify(serlectedParent.getRingHandler().getSelf().getIp(), serlectedParent.getRingHandler().getSelf().getPort());
           // serlectedParent.sendNotify(s.getRingHandler().getSelf().getIp(), s.getRingHandler().getSelf().getPort());
        }

        Thread.sleep(200);
        //server2.stop();
        BigInteger key = server2.getRingHandler().getSelf().getId().subtract(BigInteger.ONE);
        server3.addKey(key, "gravy");

        System.out.println("done");


        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("run probe");
                server3.probe();
                System.out.println("do lookup");
                server1.lookup(key);
            }
        };

        int interval = 3000;
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, 2000, interval);

    }

    private static void nNodesNoAdd(ApplicationDomain app, String ip, int n) throws InterruptedException {
        Server server1 = new Server(app, ip, 5050);
        server1.start();

        Server server2 = new Server(app, ip, 6060);
        server2.start();

        Server server3 = new Server(app, ip, 7070);
        server3.start();


        Server server4 = new Server(app, ip, 7071);
        server4.start();

        Thread.sleep(1000);

        server2.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server3.sendNotify(server2.getRingHandler().getSelf().getIp(), server2.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server4.sendNotify(server3.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server1.sendNotify(server4.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);

        List<Server> servers = new ArrayList<>();
        servers.add(server1);
        servers.add(server2);
        servers.add(server3);
        servers.add(server4);
        Thread.sleep(10000);

        for (int i = 0; i < n - 4; i++) {
            Server s = new Server(app);
            servers.add(s);
            s.start();
            Thread.sleep(500);
            System.out.println("Started: " + i);
            Server serlectedParent = servers.get(servers.size() - 2);
            s.sendNotify(serlectedParent.getRingHandler().getSelf().getIp(), serlectedParent.getRingHandler().getSelf().getPort());
            // serlectedParent.sendNotify(s.getRingHandler().getSelf().getIp(), s.getRingHandler().getSelf().getPort());
        }


        System.out.println("done");


        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("run probe");
                server3.probe();
            }
        };

        int interval = 3000;
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, 2000, interval);

    }


    private static void nNodesNoAddWithFailure(ApplicationDomain app, String ip, int n) throws InterruptedException {
        Server server1 = new Server(app, ip, 5050);
        server1.start();

        Server server2 = new Server(app, ip, 6060);
        server2.start();

        Server server3 = new Server(app, ip, 7070);
        server3.start();


        Server server4 = new Server(app, ip, 7071);
        server4.start();

        Thread.sleep(1000);

        server2.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server3.sendNotify(server2.getRingHandler().getSelf().getIp(), server2.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server4.sendNotify(server3.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);
        server1.sendNotify(server4.getRingHandler().getSelf().getIp(), server3.getRingHandler().getSelf().getPort());
        Thread.sleep(1000);

        List<Server> servers = new ArrayList<>();
        servers.add(server1);
        servers.add(server2);
        servers.add(server3);
        servers.add(server4);
        Thread.sleep(10000);

        for (int i = 0; i < n - 4; i++) {
            Server s = new Server(app);
            servers.add(s);
            s.start();
            Thread.sleep(500);
            System.out.println("Started: " + i);
            Server serlectedParent = servers.get(servers.size() - 2);
            s.sendNotify(serlectedParent.getRingHandler().getSelf().getIp(), serlectedParent.getRingHandler().getSelf().getPort());
            // serlectedParent.sendNotify(s.getRingHandler().getSelf().getIp(), s.getRingHandler().getSelf().getPort());
        }


        System.out.println("done");


        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("run probe");
                server3.probe();
            }
        };

        TimerTask killSpawn = new TimerTask() {
            @Override
            public void run() {

                int index = servers.size();
                servers.get(index).stop();
                servers.remove(index);

                Server s = new Server(app);
                servers.add(s);
                s.start();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Server serlectedParent = servers.get(servers.size() - 2);
                s.sendNotify(serlectedParent.getRingHandler().getSelf().getIp(), serlectedParent.getRingHandler().getSelf().getPort());
            }
        };

        int interval = 3000;
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, 2000, interval);
        timer.scheduleAtFixedRate(killSpawn, 5000, interval + 2000);

    }
}
