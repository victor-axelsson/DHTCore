package se.kth.networking.java.first.models;

import org.json.JSONObject;
import se.kth.networking.java.first.Helper;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;

/**
 * Created by victoraxelsson on 2016-11-06.
 */
public class Node {

    private BigInteger id;
    private String ip;
    private int port;

    public Node(String ip, int port) {
        id = Helper.doHash(ip, port);
        this.ip = ip;
        this.port = port;
    }

    public Node(String json){
        JSONObject obj = new JSONObject(json);
        this.ip = obj.optString("ip");
        this.port = obj.optInt("port");
        id = Helper.doHash(ip, port);
    }

    public BigInteger getId() {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Node node = (Node) o;

        if (port != node.port) return false;
        return ip != null ? ip.equals(node.ip) : node.ip == null;
    }

    @Override
    public int hashCode() {
        int result = ip != null ? ip.hashCode() : 0;
        result = 31 * result + port;
        return result;
    }
}
