package se.kth.networking.java.first.models;

/**
 * Created by victoraxelsson on 2016-11-04.
 */
public interface OnResponse<T> {
     /**
      * Callback method that is used in this implementation
      * @param response - response of a generic type, most frequently a String
      * @param node - node that issued the callback
      * @return a String indicating the result of the callback
      */
     String onResponse(T response, Node node);
}
