package com.example.plugin6;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

/**
 * Created by 2bab on 2017/3/13.
 */

public class PluginService extends Service {

    private static final int GET_MSG_ACTIVITY_MESSENGER = 0x2330;
    private static final int SEND_NORMAL_MSG_TO_ACTIVITY = 0x2333;

    // 该 Service 往 DisplayActivity 发消息的通道
    private Messenger activityMessenger;
    // DisplayActivity 往该 Service 发消息的通道
    private Messenger serviceMessenger;

    private HandlerThread handlerThread;

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("PluginServiceHandlerThread");
        handlerThread.start();

        Handler handler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == GET_MSG_ACTIVITY_MESSENGER) {
                    if (activityMessenger == null) {
                        // 双向链接建立完成
                        activityMessenger = msg.replyTo;

                        Message message = Message.obtain();
                        message.what = SEND_NORMAL_MSG_TO_ACTIVITY;
                        message.obj = "A Message from Plugin Service";
                        try {
                            activityMessenger.send(message);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };

        serviceMessenger = new Messenger(handler);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceMessenger.getBinder();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        handlerThread.quitSafely();
    }
}
