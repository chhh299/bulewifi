package com.bulewifi.sc803;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        LogManager.getInstance().addLog("ADMIN", "DeviceAdmin", "--", "设备管理器已成功激活！");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        LogManager.getInstance().addLog("ADMIN", "DeviceAdmin", "--", "设备管理器已停用");
    }
}
