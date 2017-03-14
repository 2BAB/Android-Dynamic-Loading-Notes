package com.example.startserviceplugin;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static final int GET_MSG_ACTIVITY_MESSENGER = 0x2330;
    private static final int SEND_NORMAL_MSG_TO_ACTIVITY = 0x2333;

    // 和 Service 双向通信用的 Messenger
    Messenger serviceMessenger;
    Messenger activityMessenger;
    private TextView testTextView;

    // 是否已经绑定
    private boolean bound = false;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == SEND_NORMAL_MSG_TO_ACTIVITY) {
                testTextView.append(msg.obj.toString() + "\n");
            }
        }
    };

    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            bound = true;
            serviceMessenger = new Messenger(service);
            if (activityMessenger == null) {
                activityMessenger = new Messenger(handler);
            }
            Message message = Message.obtain();
            message.what = GET_MSG_ACTIVITY_MESSENGER;
            message.replyTo = activityMessenger;
            try {
                serviceMessenger.send(message); // send activity messenger to service
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
            serviceMessenger = null;
        }
    };

    public void startPluginActivity() {
        try {
            Intent pluginActivityIntent = new Intent(MainActivity.this, Class.forName("com.example.plugin6.PluginActivity"));
            startActivity(pluginActivityIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void bindPluginService() {
        try {
            Intent intent = new Intent(this, Class.forName("com.example.plugin6.PluginService"));
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        testTextView = (TextView) findViewById(R.id.test_plugin_service);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindPluginService();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startPluginActivity();
            }
        }, 4000);
    }


    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
    }
}
