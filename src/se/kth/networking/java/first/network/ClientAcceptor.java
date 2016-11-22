package se.kth.networking.java.first.network;

import se.kth.networking.java.first.models.OnResponse;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by victoraxelsson on 2016-11-06.
 */
public class ClientAcceptor extends Thread{

    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private OnResponse onResponse;

    public ClientAcceptor(int port, OnResponse onResponse) throws IOException{
        this.serverSocket = new ServerSocket(port);
        this.executorService = Executors.newFixedThreadPool(5);
        this.onResponse = onResponse;
    }


    private void handleClient(Socket client) throws InterruptedException {
        executorService.execute(new ClientSocketHandler(client, onResponse));
    }

    @Override
    public void run() {
        while(true) {
            Socket client = null;
            try {
                client = serverSocket.accept();
                handleClient(client);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void shutdown() {
        try {
            this.executorService.shutdown();
            this.serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
