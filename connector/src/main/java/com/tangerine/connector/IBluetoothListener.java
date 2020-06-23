package com.tangerine.connector;

/**
 * Created by Tangerine on 2020-6-23.
 */
public interface IBluetoothListener {
    /**
     * Calls back when a connection is made
     */
     void connected();

    /**
     * Call back when disconnected
     */
     void disconnected();

    /**
     * @param data When the remote device returns information
     */
     void receivedData(byte[] data);

    /**
     * Connection timeout callback
     */
     void connectTimeout();

    /**
     * All the time reached, ready to write data
     */
     void readyToWriteData();

}
