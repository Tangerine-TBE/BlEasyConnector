package com.tangerine.connector;

import java.io.Serializable;
/**
  * Author:Administrator
  * Date:2020-6-23
  * Direction:The information carrier of a BLE device
  */
public class PairInfo implements Serializable {
    public String name;//蓝牙名称
    public String address;//蓝牙地址
    public String brand;//蓝牙设备品牌
    public String analyser;//蓝牙设备型号
    public boolean isBLEDevice;//是否是蓝牙设备
    public PairInfo() {
    }

    public PairInfo(String name, String address, String brand, String analyser,boolean isBLEDevice) {
        this.name = name;
        this.address = address;
        this.brand = brand;
        this.analyser = analyser;
        this.isBLEDevice = isBLEDevice;
    }
}
