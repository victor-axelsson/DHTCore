package se.kth.networking.java.first.models;

/**
 * Created by victoraxelsson on 2017-01-21.
 */
public interface OnAsyncResponse<T> {
    /**
     * Callback method that is used in this implementation
     * @param response - response of a generic type, most frequently a String
     * @param node - node that issued the callback
     * @return a String indicating the result of the callback
     */
    void onResponse(T response, Node node, OnResponse<T> onResponse);
}
