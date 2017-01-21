package se.kth.networking.java.first.network;

import se.kth.networking.java.first.models.OnAsyncResponse;
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
    private OnAsyncResponse onResponse;

    /**
     * Constructor for ClientAcceptor instance
     * @param port - port that will be used to accept new socket connections
     * @param onResponse - callback method to be executed after the communication
     * @throws IOException if an error occurs during communication or accept call
     */
    public ClientAcceptor(int port, OnAsyncResponse onResponse) throws IOException{
        this.serverSocket = new ServerSocket(port);
        this.executorService = Executors.newFixedThreadPool(500);
        this.onResponse = onResponse;
    }


    /**
     * Helper method to submit the new socket to asynchronously handle it
     * @param client - socket that will be used in communication
     * @throws InterruptedException if the execution of the task was interrupted
     */
    private void handleClient(Socket client) throws InterruptedException {
        System.out.println("Thread count: " + ((ThreadPoolExecutor) executorService).getActiveCount() + ", port: " + serverSocket.getLocalPort());
        executorService.submit(new ClientSocketHandler(client, onResponse));
    }

    /**
     * The actual handling of the socket on the server side, after it has been created using accept()
     */
    @Override
    public void run() {
        boolean running = true;
        while(running) {
            Socket client = null;
            try {
                client = serverSocket.accept();
                handleClient(client);
            } catch (IOException e) {
                System.err.println("Socket was closed in ClientAcceptor, stop execution of the node " +
                        serverSocket.getInetAddress().getHostName() + ":" + serverSocket.getLocalPort());
                running = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
                running = false;
            }finally {

            }
        }
    }

    /**
     * This method cleanly terminates the ClientAcceptor and frees the resources
     */
    public void shutdown() {
        executorService.shutdownNow();
        try {
            this.serverSocket.close();
            if (!executorService.awaitTermination(2000, TimeUnit.MILLISECONDS))
                System.err.println("ServerSocket did not terminate");

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
