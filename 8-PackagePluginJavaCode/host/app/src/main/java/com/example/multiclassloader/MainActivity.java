package com.example.multiclassloader;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        loadPluginClassTest();
    }

    private void loadPluginClassTest() {
        try {
            Class libProviderClazz = Class.forName("com.example.multiclassloader.PluginClassWillBeLoad");
            System.out.println("PluginClassWillBeLoad getClassloader:" + libProviderClazz.getClassLoader());
            System.out.println("LoadPluginClassTestInterface getClassloader: " + LoadPluginClassTestInterface.class.getClassLoader());
            LoadPluginClassTestInterface dexInterface = (LoadPluginClassTestInterface) libProviderClazz.newInstance();
            Toast.makeText(this, dexInterface.getPiValue() + "", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
