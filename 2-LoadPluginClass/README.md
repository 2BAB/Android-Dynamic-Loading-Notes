# Android插件化笔记-2-LoadPluginClass


## 如何获取能够被加载的 .dex 文件

准备如下两个测试类，其中TestDexInterface还需要拷贝一份到工程中

**TestDexClass.java**

```java
package example.com.classeasyload;

public class TestDexClass implements TestDexInterface{

    @Override
    public float getPiValue() {
        return 3.14f;
    }

}
```

**TestDexInterface.java**

```java
package example.com.classeasyload;

public interface TestDexInterface {

    float getPiValue();

}
```



1. `javac *.java                              -> .class`
2. `jar cvf origin.jar .                      -> .jar`
3. `dx --dex --output=target.dex origin.jar   -> .dex`

<!--more-->

按文章的步骤，自己实现了一遍，需要注意的是第二步打jar包的时候需要连包名所在的文件夹一起打进去

```
|____example
| |____com
| | |____classeasyload
| | | |____TestDexClass.class
| | | |____TestDexInterface.class
```

jar包里应该是如上的结构。

## 加载并调用.dex里面的方法

这里我先 `adb shell mkdir -p /system/dex/`, 然后`adb push target.dex /system/dex/`
如MainActivity里代码所示：

```java
File optimizedDexOutputPath = new File("/system/dex/" + "target.dex");// 外部路径
File dexOutputDir = this.getDir("dex", 0);// 无法直接从外部路径加载.dex文件，需要指定APP内部路径作为缓存目录（.dex文件会被解压到此目录）
DexClassLoader dexClassLoader = new DexClassLoader(
       optimizedDexOutputPath.getAbsolutePath(),
       dexOutputDir.getAbsolutePath(),
       null,
       getClassLoader());
try {
    Class libProviderClazz = dexClassLoader.loadClass("example.com.classeasyload.TestDexClass");
    TestDexInterface dexInterface = (TestDexInterface) libProviderClazz.newInstance();
    Toast.makeText(this, dexInterface.getPiValue() + "", Toast.LENGTH_LONG).show();
} catch (Exception e) {
    e.printStackTrace();
}
```

最终成功Toast出了`3.14`。


## 参考资料：

本系列为笔记文，文中有大量的源码解析都是引用的其他作者的成果，详见下方参考资料。

- [http://blog.csdn.net/singwhatiwanna/article/details/40283117](http://blog.csdn.net/singwhatiwanna/article/details/40283117)
- [http://blog.csdn.net/NUPTboyZHB/article/category/1204147](http://blog.csdn.net/NUPTboyZHB/article/category/1204147)
- [https://zhuanlan.zhihu.com/p/20515113](https://zhuanlan.zhihu.com/p/20515113)
