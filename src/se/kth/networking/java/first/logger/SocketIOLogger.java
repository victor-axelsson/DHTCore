package se.kth.networking.java.first.logger;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import org.json.JSONObject;

import java.net.SocketAddress;
import java.util.HashMap;

/**
 * Created by Nick on 1/31/2017.
 */
public class SocketIOLogger implements Logger {

    private HashMap<SocketAddress, SocketIOClient> connected = new HashMap<>();

    private static SocketIOLogger instance;
    final SocketIOServer server;

    private SocketIOLogger() {
        Configuration config = new Configuration();
        config.setHostname("130.229.166.144");
        config.setPort(9092);

        server = new SocketIOServer(config);

        server.addConnectListener(new ConnectListener() {
            @Override
            public void onConnect(SocketIOClient socketIOClient) {
                System.out.println(socketIOClient.getRemoteAddress() + " connected");
                addNode(socketIOClient.getRemoteAddress(), socketIOClient);
                //socketIOClient.sendEvent("probe", "you got probed!");
            }
        });

        server.addDisconnectListener(new DisconnectListener() {
            @Override
            public void onDisconnect(SocketIOClient socketIOClient) {
                System.out.println(socketIOClient.getRemoteAddress() + " is a traitorous scum!");
                removeNode(socketIOClient.getRemoteAddress());
                //socketIOClient.sendEvent("traitor", "you traitorous scum!");
            }
        });

        server.start();
    }

    private void addNode(SocketAddress address, SocketIOClient node) {
        connected.put(address, node);
    }

    private void removeNode(SocketAddress address) {
        connected.remove(address);
    }

    public void stop() {
        connected = null;
        server.stop();
        instance = null;
    }

    public static SocketIOLogger getLogger() {
        if (instance == null) instance = new SocketIOLogger();
        return instance;
    }

    @Override
    public void log(String key, String value) {
        for (SocketIOClient socket : connected.values()) {
            socket.sendEvent(key, value);
        }
    }
}
