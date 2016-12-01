package com.example.resmodification;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;

/**
 * Created by 2bab on 2016/11/15.
 */

public class CustomInstrumentation extends Instrumentation {

    private ClassLoader customClassloader;
    private Instrumentation base;

    public CustomInstrumentation(Instrumentation base, ClassLoader classLoader) {
        this.base = base;  // 如果要不注册 Activity 就能启动的方式，那么还需要 hook execStartActivity 等方法，此时会用到这个 base 的 Instrumentation
        customClassloader = classLoader;
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.newActivity(customClassloader, className, intent);
    }

}
