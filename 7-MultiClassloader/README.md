# Android插件化笔记-7-MultiClassloader

## 为什么要有 Multi Classloader

如上篇所说，我们不管要起 Activity、Service，其实都是需要注入自定义的 Classloader。而 Service 没有一个很好的简单注入点，所以才有了 Hook 上层 Classloader 的方案。这种方案有两种，都是解决多 Dex 加载的情况（不管插件化与否其实只要方法数超 65535 都是需要做多 Dex 加载）：

1. 单一 Classloader：就是上篇说到的 MultiDex 使用的反射注入 DexPathList 的前部，这种是利用了 BaseDexClassloader 的 findClass 特性，由前往后查找 Dex 文件并加载 Class；
2. 多 Classloader：利用双亲委派的 Classloader 机制，使得我们的 Classloader 可以优先于系统 Classloader 查找到 Class 并返回，通常会伴随着每个模块一个 Classloader，再由一个 HookClassloader 统一 Dispatch；

目前淘宝、微店等都是使用多 Classloader 形式来实现 Dex 文件的动态加载，隔离性强、鲁棒性好，但实现上有所不同：

1. 淘宝的 Atlas 做的是替换应用直接使用的 PathClassloader；
2. 微店、Instant-Run 使用的是替换 PathClassloader 的 parent；

本系列的尿性就是要简单，稳定，尽量不 Hook 任何系统服务，所以下面以替换 PathClassloader 的 parent 思路来讲：

## 替换 PathClassloader 的 parent

很明显我们应该在应用还没启动的时候就把这事干了，所以参考 Instant-Run，Hook 时机在 Application 的 `attachBaseContext` 里：

``` java
public class MultiClassloaderApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        replacePathClassloaderParent(base);
    }

    private void replacePathClassloaderParent(Context context) {
        ClassLoader pathClassloader = context.getClassLoader();
        DispatchClassloader dispatchClassloader = new DispatchClassloader(pathClassloader, context);
        final Class<?> clz = ClassLoader.class;
        try {
            final Field parentField = clz.getDeclaredField("parent");
            parentField.setAccessible(true);
            parentField.set(pathClassloader, dispatchClassloader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
```

## 实现 Multi Classloader

DispatchClassloader:

``` java
public class DispatchClassloader extends ClassLoader {

    private BundleClassloader dexClassLoader;
    private Context context;
    private ClassLoader origin;

    public DispatchClassloader(ClassLoader origin, Context context) {
        super(origin.getParent());
        this.origin = origin;
        this.context = context;
        installDex();
    }

    private void installDex() {
        // 这里目前只装载了一个测试 Dex，正常情况下需要装载某个目录下的所有 dex 文件（通常每个 Bundle 有一个 Dex）
        File optimizedDexOutputPath = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/7-MultiClassloader.dex");
        File dexOutputDir = context.getDir("dex", 0);
        dexClassLoader = new BundleClassloader(
                optimizedDexOutputPath.getAbsolutePath(),
                dexOutputDir.getAbsolutePath(),
                null,
                origin);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 需要在这里遍历所有 Bundle 的 Classloader，或者用包名等来做查找分发
        Class<?> clz = dexClassLoader.findClass(name);
        return clz;
    }
}
```

BundleClassloader:

``` java
public class BundleClassloader extends DexClassLoader {

    public BundleClassloader(String dexPath, String optimizedDirectory, String librarySearchPath, ClassLoader parent) {
        super(dexPath, optimizedDirectory, librarySearchPath, parent);
    }
    
    // 仅仅是用来改写 protected 签名
    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }
}
```


**几个注意点**：

1. 根据参考资料里 Google 的一段注释，如果不用 BundleClassloader 做查找转发的话，还有些隐藏 Bug。反正我们的目的本来就是需要多个 Classloader 的，就顺水推舟了；
2. 请复写 findClass 而不是 loadClass，减少不必要的改动；
3. findClass 默认 protected 的，所以需要继承 DexClassloader 改写 findClass 的签名；

**Demo 工程打包过程**：

- 先切到 /host 工程中，`./gradlew installDebug` 安装宿主工程；
- 再切到 /plugin 工程中，按 [之前文章（Android插件化笔记-2-LoadPluginClass）](http://2bab.me/2016/09/18/Android%E6%8F%92%E4%BB%B6%E5%8C%96%E7%AC%94%E8%AE%B0-2-LoadPluginClass/)打包 Dex 的办法打出插件 Dex 文件并重命名为「7-MultiClassloader.dex」；
- adb push 该文件到手机的 /sdcard/Downloads/目录下
- 启动宿主工程，toast 出 3.14 即为成功；

## 参考资料：

本系列为笔记文，文中有大量的源码解析都是引用的其他作者的成果，详见下方参考资料。

- [http://blog.csdn.net/xiangzhihong8/article/details/64906131](http://blog.csdn.net/xiangzhihong8/article/details/64906131)
- [https://android.googlesource.com/platform/tools/base/+/gradle_2.0.0/instant-run/instant-run-server/src/main/java/com/android/tools/fd/runtime/IncrementalClassLoader.java](https://android.googlesource.com/platform/tools/base/+/gradle_2.0.0/instant-run/instant-run-server/src/main/java/com/android/tools/fd/runtime/IncrementalClassLoader.java)
- [https://juejin.im/entry/59ca1d2d6fb9a00a616f496c](https://juejin.im/entry/59ca1d2d6fb9a00a616f496c)
- [https://mp.weixin.qq.com/s/p8-ABKDpMLm6T4lJdK8Y3Q](https://mp.weixin.qq.com/s/p8-ABKDpMLm6T4lJdK8Y3Q)


