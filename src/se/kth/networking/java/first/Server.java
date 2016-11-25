package se.kth.networking.java.first;

import org.json.JSONObject;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;
import se.kth.networking.java.first.network.ClientAcceptor;
import se.kth.networking.java.first.ring.RingHandler;

import java.io.IOException;
import java.util.HashMap;

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

    private boolean isValidMessage(String msg){
        return msg != null && !msg.trim().equalsIgnoreCase(":") && msg.contains(":");
    }

    private String handleMessage(String clientMessage, Node node){

        JSONObject message = new JSONObject(clientMessage);

        String response = "Bad request";
        if(isValidMessage(clientMessage)){
            switch (message.getString("type")){
                case "notify":
                    response = ringHandler.notifyPredecessor(node);
                    break;
                case "finger_probe":
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
                case "add":
                    String payload = message.getString("value");
                    long key = message.getLong("key");

                    ringHandler.addKey(key, payload);
                    break;
            }
        }

        return response;
    }

    public void addKey(int key, String value){
        ringHandler.addKey(key, value);
    }

    public void lookup(int key){
        ringHandler.lookup(key, ringHandler.getSelf());
    }

    public void sendNotify(String ip, int port){
        ringHandler.sendNotify(ip, port);
    }

    public void probe(){
        ringHandler.probe();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
       // int port = Integer.valueOf(args[0]);



        ApplicationDomain app = new ApplicationDomain() {

            HashMap<Long, String> store = new HashMap<>();

            @Override
            public void storeKey(long key, String value) {
                store.put(key, value);
                System.out.println(key + ":" + value);
            }

            @Override
            public String getKey(long key) {
                return store.get(key);
            }

            @Override
            public void foundKey(long key, String value) {
                System.out.println("Key:" + key + ", Value: " + value);
            }
        };

        Server server1 = new Server(app, 5050);
        server1.start();

        Server server2 = new Server(app, 6060);
        server2.start();

        Thread.sleep(3000);

        Server server3 = new Server(app, 7070);
        server3.start();

        //server1.sendNotify(server2.getRingHandler().getIp(), server2.getRingHandler().getPort());
        server2.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
        Thread.sleep(3000);
        server3.sendNotify(server1.getRingHandler().getSelf().getIp(), server1.getRingHandler().getSelf().getPort());
        Thread.sleep(3000);

        server2.addKey(22, "gravy");
        server2.addKey(55, "stuff");

        Thread.sleep(3000);

        server2.lookup(55);

        server3.probe();

        System.out.println("done");
    }

    private void start() {
        acceptor.start();
    }

    public RingHandler getRingHandler() {
        return ringHandler;
    }
}
