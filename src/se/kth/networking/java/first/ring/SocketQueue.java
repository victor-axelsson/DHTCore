package se.kth.networking.java.first.ring;

import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;
import se.kth.networking.java.first.network.Client;

import java.io.IOException;

/**
 * Created by victoraxelsson on 2016-12-01.
 */
public class SocketQueue {

    /**
     * Helper method that should be called to send a message to a node
     * @param node - node that should receive the message
     * @param message - String that should be send to the node
     * @param onResponse - callback that should be executed, when the respons is received
     * @throws IOException if there was a communication error using socket
     */
    public void sendMessage(Node node, String message, OnResponse onResponse) throws IOException {
//        if (node.getId().equals(from.getId())&& !isProbeMsg(message) && !isLookupMsg(message)) {
//            System.out.println("Try to message itself " + message);
//        } else {
            System.out.println("----");
            System.out.println("MSG: " + message);
            System.out.println("TO:" + node.getPort());
            System.out.println("----");

            Client c = null;
            c = new Client(node.getAsSocket(), message, onResponse);
            c.start();
//        }
    }


}
