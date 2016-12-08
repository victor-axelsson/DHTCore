package se.kth.networking.java.first;

import java.math.BigInteger;
import java.util.Map;

/**
 * Created by victoraxelsson on 2016-11-18.
 */
public interface ApplicationDomain {
    void store(BigInteger key, String value);
    String get(BigInteger key);
    void remove(BigInteger key);
    void onFound(BigInteger key, String value);
    Map<BigInteger, String> split(BigInteger from, BigInteger to);
    Map<BigInteger, String> getStore();
}
