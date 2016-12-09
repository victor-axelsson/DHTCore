package se.kth.networking.java.first.network;

import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by Nick on 11/2/2016.
 */
public class Client {
    private Socket socket;
    private static String ENDLINE = "\n";
    private String payload;
    private OnResponse<String> onResponse;

    private static final int TIMEOUT = 2000;

    /**
     * Constructor for Client instance
     * @param socket - socket to be used in communication
     * @param payload - message to be passed through the socket
     * @param onResponse - callback that should be executed, when Client receives a resoinse
     */
    public Client(Socket socket, String payload, OnResponse<String> onResponse) {
        this.socket = socket;
        this.payload = payload;
        this.onResponse = onResponse;
    }

    /**
     * Starts the Client and initiates the communication through socket
     * @throws IOException if the socket can not be used for communication anymore
     */
    public void start() throws IOException {
        BufferedReader reader = null;
        PrintWriter writer = null;
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            handleClient(reader, writer);
        } finally {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            socket.close();
        }
    }

    /**
     * Helper method to isolate the communication logic
     * @param reader - input stream received from the socket
     * @param writer - output stream received from the socket
     * @throws IOException if during the communication an error occured
     */
    private void handleClient(BufferedReader reader, PrintWriter writer) throws IOException {
        writer.write(payload + ENDLINE);
        writer.flush();

        socket.setSoTimeout(TIMEOUT);
        String reply = reader.readLine();

        if(reply == null){
            System.err.println("Reply was null for " + payload);
        }

        //The server always listens on the same port, so we can use the socket for this purpose
        String ip=(((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress()).toString().replace("/","");
        Node serverNode = new Node(ip, socket.getPort());

        if(onResponse != null){
            onResponse.onResponse(reply, serverNode);
        }
    }
}
