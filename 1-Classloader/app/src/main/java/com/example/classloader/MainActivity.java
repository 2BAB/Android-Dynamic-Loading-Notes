package com.example.classloader;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        countClassLoader();
    }

    private void countClassLoader() {
        ClassLoader classLoader = getClassLoader();
        if (classLoader != null) {
            Log.i("[CountClassLoader]", classLoader.toString());
            while (classLoader.getParent() != null) {
                classLoader = classLoader.getParent();
                Log.i("[CountClassLoader]", classLoader.toString());
            }
        }
    }
}
