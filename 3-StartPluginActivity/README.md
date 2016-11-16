# Android插件化笔记-3-StartPluginActivity

其实从这一节开始，就需要区分两种插件化的方案：

- 需要提前在 Manifest 里注册 Activity 、Service 的
- 不需要的

网路上大多是研究不需要注册的方案，需要 hook 各种 Activity、Service 的启动流程和生命周期。一般来说 hook 的原则是越少越好，越少越不会和系统的变动有冲突，自然也就不会出问题。

当然，也有不做深度 hook 的方案，比如被反编译出来的 Atlas （现在改名叫 ACDD，https://github.com/zjf1023/ACDDExtension)。下面都是按预先注册的方案来解释，这样的方案较为简单，hook的量极少，稳定可靠，当然也就牺牲了一定的动态性。

## Activity 启动需要什么

启动流程的分析网路上很多很多，这边摘了一个比较精简的版本：
>
- 每个Activity的启动过程都是通过startActivityForResult() 最终都会调用Instrument.execStartActivity()
- 再由ActivityManagerNative.startActivity() 通过 IPC AMS所在进程，ActivityManagerService.startActivity()
- 最后 ActivityStackSupervisor.startActivityLocked(),权限以及安全检查mService.checkPermission。我们的Activity如果不注册就会在这个检查时返回一个没有注册的错误，最后回到应用进程的时候抛出这个没注册的异常。
- 安全校验完成以后，会调用ApplicationThread.scheduleLaunchActivity()
- 这一步让ApplicationThread做好跳转 activity 的准备（一些数据的封装），紧接着通过handle发送消息通知app.thread要进行Activity启动调度了，然后 app.thread接收到消息的时候才开始进行调度。
- 这个message的接收是在ActivityThread中的handleMessage(Message msg)处理的。

``` java
Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "activityStart");
final ActivityClientRecord r = (ActivityClientRecord) msg.obj;
	
r.packageInfo = getPackageInfoNoCheck(
	        r.activityInfo.applicationInfo, r.compatInfo);
handleLaunchActivity(r, null);
Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
```

> - 这句中handleLaunchActivity()又调用了performLaunchActivity(r, customIntent); 而最终又调用了这句：

``` java
java.lang.ClassLoader cl = r.packageInfo.getClassLoader();
activity = mInstrumentation.newActivity(
        cl, component.getClassName(), r.intent);
StrictMode.incrementExpectedActivityCount(activity.getClass());
r.intent.setExtrasClassLoader(cl);
```

> - 兜了一圈又回到Instrumentation了。结果终于找到了可以hook的点了，就是这个mInstrumentation.newActivity()

## Hook

因为我们提前注册了 Activity，所以其实不会碰到校验的问题。剩下的问题就只有，我们的插件 Activity 代码不在当前 Classloader 里。

``` java
java.lang.ClassLoader cl = r.packageInfo.getClassLoader();
activity = mInstrumentation.newActivity(
        cl, component.getClassName(), r.intent);
```

Hook 的点也是显而易见的，在 Instrumentation 里把 ClassLoader 换掉。

Application里：

```
 	@Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        installDex();
        hookInstrumentation();
    }

	private void installDex() {
        File optimizedDexOutputPath = new File("/system/dex/" + "3-Plugin.apk");// 外部路径
        File dexOutputDir = this.getDir("dex", 0);// 无法直接从外部路径加载.dex文件，需要指定APP内部路径作为缓存目录（.dex文件会被解压到此目录）
        dexClassLoader = new DexClassLoader(
                optimizedDexOutputPath.getAbsolutePath(),
                dexOutputDir.getAbsolutePath(),
                null,
                getClassLoader());
    }

    private void hookInstrumentation() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object currentActivityThread = currentActivityThreadMethod.invoke(null);

            // 拿到原始的 mInstrumentation字段
            Field mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation");
            mInstrumentationField.setAccessible(true);
            Instrumentation mInstrumentation = (Instrumentation) mInstrumentationField.get(currentActivityThread);

            //如果没有注入过，就执行替换
            if (!(mInstrumentation instanceof CustomInstrumentation)) {
                CustomInstrumentation pluginInstrumentation = new CustomInstrumentation(mInstrumentation, dexClassLoader);
                mInstrumentationField.set(currentActivityThread, pluginInstrumentation);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```

CustomInstrumentation：

``` java
public class CustomInstrumentation extends Instrumentation {

    private ClassLoader customClassloader;
    private Instrumentation base;

    public CustomInstrumentation(Instrumentation base, ClassLoader classLoader) {
        this.base = base;  // 如果要不注册 Activity 就能启动的方式，那么还需要 hook execStartActivity 等方法，此时会用到这个 base 的 Instrumentation
        customClassloader = classLoader;
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        // 替换了 ClassLoader
        return super.newActivity(customClassloader, className, intent);
    }

}
```

至此，我们就能启动插件的 Activity 了。

## 参考资料

本系列为笔记文，文中有大量的源码解析都是引用的其他作者的成果，详见下方参考资料。

- [8个类搞定插件化——Activity实现方案](http://kymjs.com/code/2016/05/15/01)
- [Android 插件化原理解析——插件加载机制](http://weishu.me/2016/04/05/understand-plugin-framework-classloader/)
- [Android插件化（一）：使用改进的MultiDex动态加载assets中的apk](https://github.com/nuptboyzhb/AndroidPluginFramework/blob/master/%E7%AC%AC%E4%B8%80%E8%AF%BE-%E6%94%B9%E8%BF%9B%E7%9A%84MultiDex%E5%8A%A8%E6%80%81%E5%8A%A0%E8%BD%BD%E6%99%AE%E9%80%9Aapk/README.md)



