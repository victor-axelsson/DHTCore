package se.kth.networking.java.first;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Random;

/**
 * Created by victoraxelsson on 2016-11-08.
 */
public class Helper {

    private static Helper instance;

    /**
     * Helper constructor hat is private in order to limit the object initiation
     */
    //Singelton, the ctor is private to restrict access
    private Helper(){}

    /**
     * Accessor for the singleton
     * @return Helper onstance
     */
    public static Helper getHelper(){
        if (instance == null){
            instance = new Helper();
        }
        return instance;
    }

    /**
     * Gets a random integer from min to max
     * @param min - lower boundary of random
     * @param max - upper boundary of random
     * @return random integer from min to max
     */
    public int getRandom(int min, int max){
        return (int) (Math.random() * (max - min)) + min;
    }

    /**
     * Creates an unique hash value for ip and port combination using MD5 with 128-bits
     * @param ip - String representing the ip address of a node
     * @param port - Port of the node
     * @return hash value for ip and port combination
     */
    public static BigInteger doHash(String ip, int port) {
        return hash(ip + port);
    }

    /**
     * Helper for doHash
     * @param toHash merged string that should be hashed
     * @return BigInteger of hash value from toHash variable
     */
    private static BigInteger hash(String toHash)
    {
        String sha1 = "";
        try
        {
            MessageDigest crypt = MessageDigest.getInstance("MD5");
            crypt.reset();
            crypt.update(toHash.getBytes("UTF-8"));
            sha1 = byteToHex(crypt.digest());
        }
        catch(NoSuchAlgorithmException | UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }

        return new BigInteger(String.valueOf(fromHexToDecimal(sha1.toUpperCase())));
    }

    /**
     * Helper function to change a byte hash into a base-16 number
     * @param hash - byte hash to be turned into base-16 number
     * @return a base-16 number
     */
    private static String byteToHex(final byte[] hash)
    {
        Formatter formatter = new Formatter();
        for (byte b : hash)
        {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }

    /**
     * Helper that converts a base-16 number into base-10 number
     * @param s - base-16 number
     * @return base-10 number
     */
    private static BigInteger fromHexToDecimal(String s) {
        String digits = "0123456789ABCDEF";
        s = s.toUpperCase();
        BigInteger val = BigInteger.ZERO;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int d = digits.indexOf(c);
            val = val.multiply(BigInteger.valueOf(16)).add(BigInteger.valueOf(d));
        }
        return val;
    }
}
