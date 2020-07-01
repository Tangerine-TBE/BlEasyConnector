package com.tangerine.connector;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.tangerine.util.StringUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.UUID;

/**
 * Created by Tangerine on 2020-6-23.
 */
public final class BaseBleHandler  {
    private static ConnectTask mConnectTask;
    private BluetoothDevice mDevice;
    private IBluetoothListener parentListener;
    private int mStep;
    private BluetoothGatt mGatt;
    private BluetoothGattCallback mGattCallBack;
    private List<SvcChrPair> mNotifyChrs;
    private BluetoothAdapter mAdapter;
    private ScanCallback mBLEScannerCallBck;
    private BluetoothLeScanner mBLEScanner;
    private SvcChrPair p;
    private int mExpectedState;
    private Timer mTimer;
    private boolean mReTryConnect;
    private int mTime;

    private BaseBleHandler(Context context,int time) {
        this.mTime = time;

        mNotifyChrs = new ArrayList<>();
        mBLEScannerCallBck = makeScanCallBack(context);
        mGattCallBack = makeGattCallback();
    }
    /**
     * @param pairInfo           Bluetooth information for the device
     * @param iBluetoothListener Mapping relationships to remote devices
     */
    public final boolean prepare(PairInfo pairInfo, IBluetoothListener iBluetoothListener) {
        if (mExpectedState == BluetoothProfile.STATE_CONNECTED) {
            //The device has been connected and is not allowed to modify
            return false;
        }
        //The first step is to write device information when all states are good
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        parentListener = iBluetoothListener;
        assert mAdapter != null;
        mDevice = mAdapter.getRemoteDevice(pairInfo.address);
        return true;
    }

    public boolean writeReTry(UUID svc, UUID wri, byte[] any) {
        boolean failToWrite = true;
        if (mGatt != null) {
            BluetoothGattService service = mGatt.getService(svc);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(wri);
                if (characteristic != null) {
                    byte[][] moreAny = StringUtil.splitBytes(any, 20);
                    for (byte[] data : moreAny) {
                        characteristic.setValue(data);
                        boolean result = mGatt.writeCharacteristic(characteristic);
                        if (!result) {
                            failToWrite = false;
                        }
                    }
                    return failToWrite;
                }
            }
        }
        return true;
    }

    public final void connect() {
        mExpectedState = BluetoothProfile.STATE_CONNECTED;
        if (mConnectTask != null) {
            mConnectTask.cancel();
            mConnectTask = null;
        }
        new Handler(Looper.getMainLooper()).postDelayed(mConnectTask = new ConnectTask(), 2000);
    }



    private class ConnectTask implements Runnable,  ITimerListener {
        private boolean shallRun = true;

        void cancel() {
            shallRun = false;
        }


        @Override
        public void run() {
            if (mGatt != null) {
                mGatt = null;
            }
            if (!shallRun) {
                return;
            }
            mTimer = new Timer();
            BaseTimerTask mBaseTimeTask = new BaseTimerTask(this);
            mTimer.schedule(mBaseTimeTask,0,mTime);
            mBLEScanner = mAdapter.getBluetoothLeScanner();
            mStep = 0;
            mBLEScanner.startScan(mBLEScannerCallBck);
        }

        @Override
        public void onTime() {
            if (mTimer != null){
                mTimer.cancel();
                mTimer  = null;
                mReTryConnect = true;
            }
        }
    }

    private ScanCallback makeScanCallBack(final Context context) {

        return new ScanCallback() {

            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                mStep++;
                BluetoothDevice device = result.getDevice();
                if (device.getAddress().equals(mDevice.getAddress())) {
                    mBLEScanner.stopScan(this);
                    mGatt = device.connectGatt(context, false, mGattCallBack);
                }
                if (mReTryConnect) {
                    mBLEScanner.stopScan(this);
                    connect();
                    mReTryConnect = false;
                }
            }
        };
    }

    /**
     * Register for listening for the Bluetooth service
     *
     * @param svc The UUID of the Bluetooth service
     * @param chr The UUID of the Bluetooth characteristic
     */
    public final void registerChrNotify(UUID svc, UUID chr) {
        p = new SvcChrPair(svc, chr);
        if (mNotifyChrs.contains(p)) {
            return;
        }
        if (mGatt == null) {
            return;
        }
        BluetoothGattService service = mGatt.getService(svc);
        if (service == null) {
            return;
        }
        BluetoothGattCharacteristic ch = service.getCharacteristic(chr);
        boolean can = mGatt.setCharacteristicNotification(ch, true);
        for (BluetoothGattDescriptor descriptor : ch.getDescriptors()) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            boolean success = mGatt.writeDescriptor(descriptor);
        }
        mNotifyChrs.add(p);
    }

    public final void unregisterChrNotify(UUID svc, UUID chr) {
        SvcChrPair p = new SvcChrPair(svc, chr);
        if (!mNotifyChrs.contains(p) || mGatt == null) {
            return;
        }
        BluetoothGattCharacteristic ch = mGatt.getService(svc).getCharacteristic(chr);
        mGatt.setCharacteristicNotification(ch, false);
        for (BluetoothGattDescriptor descriptor : ch.getDescriptors()) {
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            mGatt.writeDescriptor(descriptor);
        }
        mNotifyChrs.remove(p);
    }

    private static class SvcChrPair {
        UUID svcUid;
        UUID chrUid;

        SvcChrPair(UUID svc, UUID wri) {
            svcUid = svc;
            chrUid = wri;
        }


        public boolean equals(Object another) {
            if (!(another instanceof SvcChrPair)) {
                return false;
            }
            SvcChrPair ano = (SvcChrPair) another;
            return svcUid.equals(ano.svcUid) && chrUid.equals(ano.chrUid);
        }
    }

    private boolean refreshDevicesCache() {
        if (mGatt != null) {
            try {
                Method method = mGatt.getClass().getMethod("refresh", new Class[0]);
                if (method != null) {
                    boolean boost = (Boolean) method.invoke(
                            mGatt, new Object[0]);
                    return boost;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return false;
    }

    private BluetoothGattCallback makeGattCallback() {
        return new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    switch (newState) {
                        case BluetoothProfile.STATE_CONNECTED:
                            if (mExpectedState == BluetoothProfile.STATE_DISCONNECTED) {
                                disconnect();
                                parentListener.disconnected();
                                return;
                            }
                            parentListener.connected();
                            gatt.discoverServices();
                            break;
                        case BluetoothProfile.STATE_DISCONNECTED:
                            parentListener.disconnected();
                            if (mExpectedState == BluetoothProfile.STATE_CONNECTED) {
                                unregisterChrNotify(p.svcUid, p.chrUid);
                                connect();
                            }
                            break;
                    }
                } else {
                    parentListener.disconnected();
                    if (mExpectedState == BluetoothProfile.STATE_CONNECTED) {
                        disconnect();
                        connect();
                    }
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    parentListener.readyToWriteData();
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic chr, int status) {
                byte[] raw = chr.getValue();
                parentListener.receivedData(raw);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic chr, int status) {

            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic chr) {
                byte[] raw = chr.getValue();
                parentListener.receivedData(raw);
            }
        };
    }

    public final void disconnect() {
        mExpectedState = BluetoothProfile.STATE_DISCONNECTED;
        unregisterChrNotify(p.svcUid, p.chrUid);
        parentListener.disconnected();
        if (mGatt != null) {
//            refreshDevicesCache();
            mGatt.disconnect();
            mGatt.close();
            mGatt = null;
        }
        if (mBLEScanner != null) {

            mBLEScanner.stopScan(mBLEScannerCallBck);
        }
    }
}


