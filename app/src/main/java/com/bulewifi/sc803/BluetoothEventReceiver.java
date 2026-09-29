package com.bulewifi.sc803;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class BluetoothEventReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        LogManager logManager = LogManager.getInstance();

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            handleAclEvent(intent, "CONNECTED");
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            handleAclEvent(intent, "DISCONNECTED");
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
            handleAclEvent(intent, "DISCONNECT_REQ");
        } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            handleStateChanged(intent);
        } else {
            // Other actions if registered
            logManager.addLog("BROADCAST", "--", "--", "Action: " + action);
        }
    }

    private void handleAclEvent(Intent intent, String eventType) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String name = "(Unknown)";
        String address = "--";
        StringBuilder detail = new StringBuilder();

        if (device != null) {
            try {
                name = device.getName();
            } catch (SecurityException e) {
                name = "(SecurityException)";
            }
            address = device.getAddress();

            // Bond state
            int bondState = device.getBondState();
            detail.append("Bond: ").append(getBondStateString(bondState));

            // Bluetooth Type
            int type = device.getType();
            detail.append(", Type: ").append(getDeviceTypeString(type));

            // Bluetooth Class
            BluetoothClass btClass = device.getBluetoothClass();
            if (btClass != null) {
                detail.append(", Class: 0x").append(Integer.toHexString(btClass.getDeviceClass()));
            }
        } else {
            detail.append("EXTRA_DEVICE is null");
        }

        // Dump any additional intent extras (like disconnect reasons)
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                if (!BluetoothDevice.EXTRA_DEVICE.equals(key)) {
                    Object val = extras.get(key);
                    detail.append(", ").append(key).append("=").append(val);
                }
            }
        }

        LogManager.getInstance().addLog(eventType, name, address, detail.toString());
    }

    private void handleStateChanged(Intent intent) {
        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
        int prevState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.ERROR);

        String detail = String.format("BT State: %s -> %s",
                getBtStateString(prevState), getBtStateString(state));

        LogManager.getInstance().addLog("BT_STATE", "LocalAdapter", "--", detail);
    }

    private String getBondStateString(int state) {
        switch (state) {
            case BluetoothDevice.BOND_BONDED:
                return "BONDED";
            case BluetoothDevice.BOND_BONDING:
                return "BONDING";
            case BluetoothDevice.BOND_NONE:
                return "NONE";
            default:
                return "UNKNOWN(" + state + ")";
        }
    }

    private String getDeviceTypeString(int type) {
        switch (type) {
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                return "CLASSIC";
            case BluetoothDevice.DEVICE_TYPE_LE:
                return "LE";
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                return "DUAL";
            case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
            default:
                return "UNKNOWN";
        }
    }

    public static String getBtStateString(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_OFF:
                return "OFF";
            case BluetoothAdapter.STATE_TURNING_ON:
                return "TURNING_ON";
            case BluetoothAdapter.STATE_ON:
                return "ON";
            case BluetoothAdapter.STATE_TURNING_OFF:
                return "TURNING_OFF";
            default:
                return "UNKNOWN(" + state + ")";
        }
    }
}
