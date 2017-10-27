package com.example.loadpluginresource;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Handler handler = new Handler();
        handler.postDelayed(goToPluginActivity, 1500);
    }

    Runnable goToPluginActivity = new Runnable() {
        @Override
        public void run() {
            try {
                // 启动 Plugin Activity
                Intent intent = new Intent(MainActivity.this, MyApplication.dexClassLoader.loadClass("com.example.plugin4.MainActivity"));
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
}
