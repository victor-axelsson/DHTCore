package se.kth.networking.java.first;

import java.math.BigInteger;
import java.util.Map;

/**
 * Created by victoraxelsson on 2016-11-18.
 */
public interface ApplicationDomain {
    /**
     * Stores a key/value pair in the storage on the current node
     * @param key - BigInteger that should be used to access the value
     * @param value - String that should be stored under the key
     */
    void store(BigInteger key, String value);

    /**
     * Method to get a value by key from storage
     * @param key - BigInteger that should be used to access the value
     * @return value from storage that is stored under the key
     */
    String get(BigInteger key);

    /**
     * Method to remove a value from storage by key
     * @param key - BigInteger that should be used to access the value
     */
    void remove(BigInteger key);

    /**
     * Method that is called, when a key/value pair is found
     * @param key - BigInteger that was used to access the value
     * @param value - String that was stored under the key
     */
    void onFound(BigInteger key, String value);

    /**
     * Helper method to remove a set of keys with ids that are between from and to from storage in order to pass them to
     * another node
     * @param from - lower boundary of the interval
     * @param to - upper boundary of the interval
     * @return a set of keys with ids that are between from and to from storage
     */
    Map<BigInteger, String> split(BigInteger from, BigInteger to);
}
