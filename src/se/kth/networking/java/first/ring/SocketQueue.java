package se.kth.networking.java.first.ring;

import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;
import se.kth.networking.java.first.network.Client;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by victoraxelsson on 2016-12-01.
 */
public class SocketQueue {


    public SocketQueue(){

    }

    public void sendMessage(Node node, String message, OnResponse onResponse) throws IOException{
        System.out.println("----");
        System.out.println("MSG: " + message);
        System.out.println("TO:" + node.getPort());
        System.out.println("----");

        Client c = null;
        c = new Client(node.getAsSocket(), message, onResponse);
        c.start();
    }


}
