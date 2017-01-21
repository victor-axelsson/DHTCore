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

    /**
     * Constructor of a node from an ip and port pair
     * @param ip - ip address that can be used to communicate with the node
     * @param port - portthat can be used to communicate with the node
     */
    public Node(String ip, int port) {
        id = Helper.doHash(ip, port);
        this.ip = ip;
        this.port = port;
    }

    /**
     * Constructor of a node from a String that represents a JSONObject
     * @param json - String that has an ip and port that will be used in node creation
     */
    public Node(String json){
        JSONObject obj = new JSONObject(json);
        this.ip = obj.optString("ip");
        this.port = obj.optInt("port");
        id = Helper.doHash(ip, port);
    }

    /**
     * Getter for id
     * @return id of the node
     */
    public BigInteger getId() {
        return id;
    }

    /**
     * Getter for ip
     * @return ip of the node
     */
    public String getIp() {
        return ip;
    }

    /**
     * Getter for port
     * @return port of the node
     */
    public int getPort() {
        return port;
    }

    /**
     * Method that is called for communication with the node
     * @return Socket instance with the current node ip and port
     * @throws IOException when the socket can not be created
     */
    public Socket getAsSocket() throws IOException {
        return new Socket(ip, port);
    }

    /**
     * Representation of the node as a JSONObject that is later converted to a String
     * @return a String representing the Node in JSON notation
     */
    @Override
    public String toString() {
        JSONObject obj = new JSONObject();
        obj.put("id", id.toString());
        obj.put("ip", ip);
        obj.put("port", port);
        return obj.toString();
    }

    /**
     * @param o - the object with which the Node will be compared
     * @return true, if the ip and port of o are the same as the current node, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Node node = (Node) o;

        if (port != node.port) return false;
        return ip != null ? ip.equals(node.ip) : node.ip == null;
    }

    /**
     * Hashcode for use of nodes as keys in HashMap or HashSet
     * @return hashcode of ip and port
     */
    @Override
    public int hashCode() {
        int result = ip != null ? ip.hashCode() : 0;
        result = 31 * result + port;
        return result;
    }
}
