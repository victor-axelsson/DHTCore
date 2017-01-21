package se.kth.networking.java.first.network;

import org.json.JSONObject;
import se.kth.networking.java.first.models.Node;
import se.kth.networking.java.first.models.OnAsyncResponse;
import se.kth.networking.java.first.models.OnResponse;

import javax.jws.Oneway;
import java.io.*;
import java.net.Socket;

/**
 * Created by Nick on 11/2/2016.
 */
public class ClientSocketHandler implements Runnable {
    private Socket client;
    private OnAsyncResponse<String> onResponse;

    /**
     * Constructor for ClientSocketHandler instance
     * @param client - the socket to be used in communication
     * @param onResponse - callback method to be executed after the response is received
     */
    public ClientSocketHandler(Socket client, OnAsyncResponse<String> onResponse) {
        this.client = client;
        this.onResponse = onResponse;
    }

    /**
     * The actual handling of the socket on the client side
     */
    @Override
    public void run() {
        BufferedReader reader = null;
        PrintWriter writer = null;
        try {
            reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream()));
            handle(reader, writer);
        } catch (IOException e) {
            e.printStackTrace(System.err);
        } finally {
            if (writer != null) writer.close();
            if (reader != null) try {
                reader.close();
                client.close();
            } catch (IOException e) {
                System.err.println(e.getCause() + " " + e.getMessage());
            }
        }
    }

    /**
     * Method to properly execute callback
     * @param msg - String that contains the resopnse
     * @return String that is the result of callback method
     */
    private void deliverMessage(String msg, OnResponse<String> callback){
        JSONObject obj = new JSONObject(msg);
        Node n = new Node(msg);
        onResponse.onResponse(obj.toString(), n, callback);
    }

    /**
     * Helper method to encapsulate the socket handling
     * @param reader - input stream received from the socket
     * @param writer - output stream received from the socket
     */
    private void handle(BufferedReader reader, PrintWriter writer) {
        //Read the message
        String str;
        try {
            while ((str = reader.readLine()) != null) {
                 deliverMessage(str, new OnResponse<String>() {
                    @Override
                    public String onResponse(String response, Node node) {
                        writer.write(response + "\n");
                        writer.flush();

                        return response;
                    }
                 });
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }
}
