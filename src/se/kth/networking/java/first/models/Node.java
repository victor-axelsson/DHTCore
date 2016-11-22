package se.kth.networking.java.first.models;

import org.json.JSONObject;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by victoraxelsson on 2016-11-06.
 */
public class Node {

    private int id;
    private String ip;
    private int port;

    public Node(String ip, int port) {
        id = (ip + port).hashCode();
        this.ip = ip;
        this.port = port;
    }

    public Node(String json){
        JSONObject obj = new JSONObject(json);
        this.ip = obj.optString("ip");
        this.port = obj.optInt("port");
        id = (this.ip + this.port).hashCode();
    }

    public int getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public Socket getAsSocket() throws IOException {
        return new Socket(ip, port);
    }

    @Override
    public String toString() {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("ip", ip);
        obj.put("port", port);
        return obj.toString();
    }

}
