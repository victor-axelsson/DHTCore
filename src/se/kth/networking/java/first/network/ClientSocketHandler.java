package se.kth.networking.java.first.network;

import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnResponse;

import java.io.*;
import java.net.Socket;

/**
 * Created by Nick on 11/2/2016.
 */
public class ClientSocketHandler implements Runnable {
    private Socket client;
    private static String END = "End";
    private OnResponse<String> onResponse;

    public ClientSocketHandler(Socket client, OnResponse<String> onResponse) {
        this.client = client;
        this.onResponse = onResponse;
    }

    @Override
    public void run() {
        BufferedReader reader = null;
        PrintWriter writer = null;
        try {
            reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream()));
            handle(reader, writer);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) writer.close();
            if (reader != null) try {
                reader.close();
            } catch (IOException e) {
                System.err.println(e.getCause() + " " + e.getMessage());
            }
        }
    }

    private String deliverMessage(String msg){

        //Do the message parsing
        String[] parts = msg.split(":");
        String[] msgArgs = parts[1].split(",");

        Node n = new Node(msgArgs[0], Integer.parseInt(msgArgs[1]));

        return  onResponse.onResponse(msg, n);
    }

    private void handle(BufferedReader reader, PrintWriter writer) {
        //Read the message
        String str;
        try {
            while ((str = reader.readLine()) != null && !END.equalsIgnoreCase(str)) {
                String res = deliverMessage(str);
                writer.write(res + "\n");
                writer.flush();
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            onResponse.onResponse("error:" + e.getMessage(), null);
        }
    }
}
