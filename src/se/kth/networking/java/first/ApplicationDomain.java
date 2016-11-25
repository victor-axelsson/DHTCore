package se.kth.networking.java.first;

/**
 * Created by victoraxelsson on 2016-11-18.
 */
public interface ApplicationDomain {
    void storeKey(long key, String value);
    String getKey(long key);
    void foundKey(long key, String value);
}
