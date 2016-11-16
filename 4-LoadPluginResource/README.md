# Android插件化笔记-4-LoadPluginResource

插件化的资源加载大体上也分两种：

- 每个插件构造单一的 `Resource` 对象，各个插件的资源互不影响
- 所有插件的资源都加载到一个 `AssetManager`，全局可用，但是会出现资源 ID 冲突的现象，必须在打包流程中做修改

本节以构造单一对象为基础讲解，资源冲突的问题和方案下节讲。

## 资源的寻找过程

> 
在Activity中的getResources()方法会走到ContextWrapper的实现上，而ContextWrapper顾名思义它只是一个包装类，最终的调用是ContextWrapper的实际类ContextImpl中的方法。
>
ContextImpl中getResources()方法返回了它的成员变量mResource,我们看一下ContextImpl的构造函数，其中mResources被第一次赋值是通过下面的函数调用:

 ```  
 Resources resources = packageInfo.getResources(mainThread);
 ```
 
> packageInfo是一个LoadedApk类型的参数，mainThread是ActivityThread类型的参数，mainThread就是当前Apk运行的主进程类，我们继续看LoadedApk中的方法：

``` 
 public Resources getResources(ActivityThread mainThread) {
    if (mResources == null) {
        mResources = mainThread.getTopLevelResources(mResDir, mSplitResDirs, mOverlayDirs,
                    mApplicationInfo.sharedLibraryFiles, Display.DEFAULT_DISPLAY, null, this);
    }
    return mResources;
 }
```


```
Resources getTopLevelResources(String resDir, String[] splitResDirs, String[] overlayDirs,
            String[] libDirs, int displayId, Configuration overrideConfiguration,
            LoadedApk pkgInfo) {
    return mResourcesManager.getTopLevelResources(resDir, splitResDirs, overlayDirs, libDirs,
                displayId, overrideConfiguration, pkgInfo.getCompatibilityInfo(), null);
}
```

> mResourceManager是一个ResourceManager类型的成员变量，当我们戳开ResourceManager的代码时，惊喜的发现这个类是一个单例，然后定位到getTopLevelResources方法

``` java
      ResourcesKey key = new ResourcesKey(resDir, displayId, overrideConfiguration, scale, token);
      Resources r;
      WeakReference<Resources> wr = mActiveResources.get(key);
      r = wr != null ? wr.get() : null;
      if (r != null && r.getAssets().isUpToDate()) {
          return r;
      }
      AssetManager assets = new AssetManager();
      if (resDir != null) {
            if (assets.addAssetPath(resDir) == 0) {
                return null;
            }
      }
      r = new Resources(assets, dm, config, compatInfo, token);
      WeakReference<Resources> wr = mActiveResources.get(key);
      Resources existing = wr != null ? wr.get() : null;
      if (existing != null && existing.getAssets().isUpToDate()) {
          r.getAssets().close();
          return existing;
      }
      mActiveResources.put(key, new WeakReference<Resources>(r));
      return r;
```

至此，我们知道了资源的真正载入和管理是由 AssetManager 来实现的，那么 Hook 点也知道了——修改 ContextImpl 的 Resource 对象所持有的 AssetManager。

## Hook

在 Plugin 的 Activity 里实现资源加载：

``` java
public class MainActivity extends AppCompatActivity {
    private Resources pluginR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 如果不 Hook mResource，也可以直接 getPluginR 来获取 values 的资源，但是无法装载 Layout
        // getPluginR().getString(R.string.plugin_string_res); //

        // Fragment 或者 自定义 View 等需要自己 Inflate 的也支持
        /*int bundleLayoutId = R.layout.activity_main;
        View bundleView = LayoutInflater.from(this).inflate(bundleLayoutId, null);
        setContentView(bundleView);*/

        setContentView(R.layout.activity_main);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        try {
            Field field = newBase.getClass().getDeclaredField("mResources");
            field.setAccessible(true);
            field.set(newBase, getPluginR(newBase));
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.attachBaseContext(newBase);
    }

    public Resources getPluginR(Context context) {
        if (pluginR != null) {
            return pluginR;
        }
        try {
            String dexPath = "/system/dex/" + "4-Plugin.apk";
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, dexPath);
            pluginR = new Resources(assetManager, context.getResources().getDisplayMetrics(), context.getResources().getConfiguration());

            //独立使用Resource时（不hook mResource）
            //Resources origin = super.getResources();
            //pluginR = new Resources(assetManager, origin.getDisplayMetrics(), origin.getConfiguration());
            return pluginR;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
```

## 参考资料

本系列为笔记文，文中有大量的源码解析都是引用的其他作者的成果，详见下方参考资料。

- [ANDROID应用程序插件化研究之ASSETMANAGER](http://www.liuguangli.win/archives/370)
- [插件化-资源处理](http://www.jianshu.com/p/96d5b83ca26c)
- [Android插件化（三）加载插件apk中的Resource资源](http://blog.csdn.net/nupt123456789/article/details/50414175)
