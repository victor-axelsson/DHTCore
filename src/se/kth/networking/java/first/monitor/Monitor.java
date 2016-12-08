package se.kth.networking.java.first.monitor;

import se.kth.networking.java.first.Helper;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by victoraxelsson on 2016-12-08.
 */
public class Monitor {

    HashMap<String, MonitorModel> listeners;
    Timer pingTimer;

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

    private void pingAll(){
        for (MonitorModel model : listeners.values()) {
            Socket s = null;
            try {
                s = model.getNode().getAsSocket();
            } catch (IOException e) {
                model.getOnDead().onResponse(model.getKey(), model.getNode());
                removeMonitor(model.getKey());
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
    }

    public void addMonitor(MonitorModel listener){
        listeners.put(listener.getKey(), listener);
    }

    public void removeMonitor(String key){
        listeners.remove(key);
    }

    public void stop(){
        pingTimer.cancel();
        pingTimer.purge();
        listeners = null;
        pingTimer = null;
    }

}
