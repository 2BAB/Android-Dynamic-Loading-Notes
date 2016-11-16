# Android插件化笔记-1-ClassLoader

## 有几个ClassLoader

如MainActivity的代码所示，

```java
 protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ClassLoader classLoader = getClassLoader();
        if (classLoader != null) {
            Log.i("[onCreate]", classLoader.toString());
            while (classLoader.getParent() != null) {
                classLoader = classLoader.getParent();
                Log.i("[onCreate While]", classLoader.toString());
            }
        }
    }
```

打印出来的结果是

```xml
I/[onCreate]: dalvik.system.PathClassLoader[DexPathList[[zip file "/data/app/example.com.classloaderdemo-1/base.apk"],nativeLibraryDirectories=[/vendor/lib, /system/lib]]]
I/[onCreate While]: com.android.tools.fd.runtime.IncrementalClassLoader@1d4251ea
I/[onCreate While]: java.lang.BootClassLoader@2f2e5cdb
```

卧槽，怎么有三个，这跟文章说的只有两个不一样啊 - -，而且我也只听师兄说过PathClassLoader和BootClassLoader，没见过这个IncrementalClassLoader啊，它是什么鬼？

<!--more-->

随手一搜，[一篇结果链接](http://www.cnblogs.com/coding-way/p/5443718.html)，发现这是因为我用了 Instant Run 而出现的，再一想，嗯，Instant Run 本身也就是一种热修复的方式，思路就是把改动的地方打到dex里然后再用IncrementalClassLoader设置成app的ClassLoader的parent，即可拦截所有类加载的动作，从而实现动态增量加载。



## 创建自己的ClassLoader实例

```java
   /**
     * Constructs a new instance of this class with the system class loader as
     * its parent.
     */
    protected ClassLoader() {
        this(getSystemClassLoader(), false);
    }

    /**
     * Constructs a new instance of this class with the specified class loader
     * as its parent.
     *
     * @param parentLoader
     *            The {@code ClassLoader} to use as the new class loader's
     *            parent.
     */
    protected ClassLoader(ClassLoader parentLoader) {
        this(parentLoader, false);
    }

    /*
     * constructor for the BootClassLoader which needs parent to be null.
     */
    ClassLoader(ClassLoader parentLoader, boolean nullAllowed) {
        if (parentLoader == null && !nullAllowed) {
            throw new NullPointerException("parentLoader == null && !nullAllowed");
        }
        parent = parentLoader;
    }
```

文章提到

> 整个Android系统里所有的ClassLoader实例都会被一棵树关联起来，这也是ClassLoader的 双亲代理模型（Parent-Delegation Model）的特点。

没提到的是，ClassLoader有三个构造器，根节点的BootClassLoader自然就是不需要parent的，如注释所写


## 使用ClassLoader一些需要注意的问题

> 在Java中，只有当两个实例的类名、包名以及加载其的ClassLoader都相同，才会被认为是同一种类型。
> 故，不可用与「加载旧类的ClassLoader」没有树的继承关系的「另一个ClassLoader」来加载新类，会出现类型不符合的异常。


## DexClassLoader 和 PathClassLoader

> DexClassLoader可以加载jar/apk/dex，可以从SD卡中加载未安装的apk；
  PathClassLoader只能加载系统中已经安装过的apk；

> optimizedDirectory必须是一个内部存储路径，还记得我们之前说过的，无论哪种动态加载，加载的可执行文件一定要存放在内部存储。DexClassLoader可以指定自己的optimizedDirectory，所以它可以加载外部的dex，因为这个dex会被复制到内部路径的optimizedDirectory；而PathClassLoader没有optimizedDirectory，所以它只能加载内部的dex，这些大都是存在系统中已经安装过的apk里面的。

## 加载类的过程

ClassLoader.loadClass() -> BaseDexClassLoader.findClass() -> DexPathList->findClass()

```java
public Class findClass(String name) {
        for (Element element : dexElements) {
            DexFile dex = element.dexFile;
            if (dex != null) {
                Class clazz = dex.loadClassBinaryName(name, definingContext);
                if (clazz != null) {
                    return clazz;
                }
            }
        }
        return null;
    }
public Class loadClassBinaryName(String name, ClassLoader loader) {
        return defineClass(name, loader, mCookie);
    }
private native static Class defineClass(String name, ClassLoader loader, int cookie);
```

## 参考资料

本系列为笔记文，文中有大量的源码解析都是引用的其他作者的成果，详见下方参考资料。

- https://zhuanlan.zhihu.com/p/20524252