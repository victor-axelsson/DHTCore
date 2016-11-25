package se.kth.networking.java.first;

import java.math.BigInteger;

/**
 * Created by victoraxelsson on 2016-11-18.
 */
public interface ApplicationDomain {
    void storeKey(BigInteger key, String value);
    String getKey(BigInteger key);
    void foundKey(BigInteger key, String value);
}
