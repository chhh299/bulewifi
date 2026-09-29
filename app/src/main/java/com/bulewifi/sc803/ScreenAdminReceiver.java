package com.bulewifi.sc803;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        LogManager.getInstance().addInfoLog("自动熄屏设备管理器已激活！");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        LogManager.getInstance().addInfoLog("自动熄屏设备管理器已停用");
    }
}
