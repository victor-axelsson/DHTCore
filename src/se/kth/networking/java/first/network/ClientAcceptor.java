package se.kth.networking.java.first.network;

import se.kth.networking.java.first.models.OnResponse;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by victoraxelsson on 2016-11-06.
 */
public class ClientAcceptor extends Thread{

    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private OnResponse onResponse;

    public ClientAcceptor(int port, OnResponse onResponse) throws IOException{
        this.serverSocket = new ServerSocket(port);
        this.executorService = Executors.newFixedThreadPool(500);
        this.onResponse = onResponse;
    }


    private void handleClient(Socket client) throws InterruptedException {
        System.out.println("Count: " + ((ThreadPoolExecutor) executorService).getActiveCount());
        executorService.submit(new ClientSocketHandler(client, onResponse));
    }

    @Override
    public void run() {
        boolean running = true;
        while(running) {
            Socket client = null;
            try {
                client = serverSocket.accept();
                System.out.println("Client acceptor accepting");
                handleClient(client);
            } catch (IOException e) {
                System.out.println("Socket was closed in ClientAcceptor, stop execution of the node " +
                        serverSocket.getInetAddress().getHostName() + ":" + serverSocket.getLocalPort());
                running = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
                running = false;
            }finally {

            }
        }
    }

    public void shutdown() {
        executorService.shutdownNow();
        try {
            this.serverSocket.close();
            if (!executorService.awaitTermination(500, TimeUnit.MILLISECONDS))
                System.err.println("ServerSocket did not terminate");

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
