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

    public static long doHash(String ip, int port) {
        return hash(ip + port);
    }

    private static long hash(String toHash)
    {
        String sha1 = "";
        try
        {
            MessageDigest crypt = MessageDigest.getInstance("SHA-1");
            crypt.reset();
            crypt.update(toHash.getBytes("UTF-8"));
            sha1 = byteToHex(crypt.digest());
        }
        catch(NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
        catch(UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }
        return fromHexToDecimal(sha1.toUpperCase());
    }

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

    private static long fromHexToDecimal(String s) {
        String digits = "0123456789ABCDEF";
        s = s.toUpperCase();
        int val = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int d = digits.indexOf(c);
            val = 16*val + d;
        }
        return val;
    }
}
