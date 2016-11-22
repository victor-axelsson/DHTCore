package se.kth.networking.java.first;

/**
 * Created by victoraxelsson on 2016-11-18.
 */
public interface ApplicationDomain {
    void storeKey(int key, String value);
    String getKey(int key);
    void foundKey(int key, String value);
}
