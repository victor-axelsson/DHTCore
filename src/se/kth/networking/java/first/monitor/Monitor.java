package se.kth.networking.java.first.monitor;

import se.kth.networking.java.first.Helper;
import se.kth.networking.java.first.models.Node;

import java.io.IOException;
import java.net.Socket;
import java.util.*;

/**
 * Created by victoraxelsson on 2016-12-08.
 */
public class Monitor {

    HashMap<String, MonitorModel> listeners;
    Timer pingTimer;

    /**
     * Constructor for Monitor, initializes the listeners with an empty map and starts a task to ping all of the nodes
     * in it every 2 seconds
     */
    public Monitor(){
        listeners = new HashMap<>();

        TimerTask pingTask = new TimerTask() {
            @Override
            public void run() {
               pingAll();
            }
        };

        //Set a random day so that the stabalizers don't run at the same time
        int interval = 2000;
        int delay = Helper.getHelper().getRandom(200, interval);

        pingTimer = new Timer();
        pingTimer.scheduleAtFixedRate(pingTask, delay, interval);
    }

    /**
     * A method to go through the listeners HashMap and try to ping every MonitorModel in it. If it is not responsive,
     * the callback is executed.
     */
    private void pingAll(){
        List<String> keysToRemove = new ArrayList<>();
        for (MonitorModel model : listeners.values()) {
            Socket s = null;
            try {
                s = model.getNode().getAsSocket();
            } catch (IOException e) {
                model.getOnDead().onResponse(model.getKey(), model.getNode());
                keysToRemove.add(model.getKey());
            }finally {
                if(s != null){
                    try {
                        s.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
        }

        for (String key : keysToRemove) removeMonitor(key);
    }

    /**
     * Adds a MonitorModel to the listeners. After addition the node starts being checked in pingAll
     * @param listener - a MonitorModel to be added to listeners
     */
    public void addMonitor(MonitorModel listener){
        listeners.put(listener.getKey(), listener);
    }

    /**
     * Removes a MonitorModel from listeners. After removal the node stops being checked in pingAll
     * @param key
     */
    public void removeMonitor(String key){
        listeners.remove(key);
    }

    /**
     * Stops the Monitor activity, purges the pingTimer and cancels future tasks for it, also cleares listeners
     */
    public void stop(){
        pingTimer.cancel();
        pingTimer.purge();
        listeners = null;
        pingTimer = null;
    }

    /**
     * A method to check if a Node is responsive
     * @param node - node to be checked
     * @return true, if the node can be reached, false otherwise
     */
    public static boolean isResponsive(Node node) {
        Socket s = null;
        try {
            s = node.getAsSocket();
            return true;
        } catch (IOException e) {
            e.printStackTrace(System.err);
            return false;
        } finally {
            if (s != null) try {
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
