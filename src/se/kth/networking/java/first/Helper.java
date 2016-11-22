package se.kth.networking.java.first;

import java.util.Random;

/**
 * Created by victoraxelsson on 2016-11-08.
 */
public class Helper {

    private static Helper instance;


    //Singelton, the ctor is private to restrict access
    private Helper(){}

    public static Helper getHelper(){
        if (instance == null){
            instance = new Helper();
        }
        return instance;
    }

    public int getRandom(int min, int max){
        return (int) (Math.random() * (max - min)) + min;
    }
}
