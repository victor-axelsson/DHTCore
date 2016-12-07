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

    private static final int TIMEOUT = 5000;

    public Client(Socket socket, String payload, OnResponse<String> onResponse) {
        this.socket = socket;
        this.payload = payload;
        this.onResponse = onResponse;
    }


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
        }
    }

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
