# 对不在SDK中公开API调用的优化方案

## 背景
Android各个厂商可能会对系统的一些API进行定制，导致系统原本API不能正常工作或不能满足需求。

这时就需要针对不同的ROM而调用不同的API，如果这些API是系统原本就有的话还比较简单，可是大部分API都是厂商定制的。由于我们没有厂商的SDK，导致我们可能就需要反射才能调用。

反射虽然很强大，但是大量的反射调用会严重影响代码可读性和可维护性。所以这里提供了一个新的解决方案，目的是**减少一部分反射调用**。

**前提：针对运行时可以访问到的API，且访问修饰符没有限制**

存在的限制：
* 不支持私有的API，如果API是私有的，只能反射调用
* 不能支持厂商定制的系统API，例如厂商在系统API的基础上添加了新的方法属性等
* 方法返回值也需要完全匹配才可以调用，这里和反射调用略有不同
* 系统服务相关的AIDL class可以声明为接口，比如`IXxxManager.Stub.asInterface(b)`

## 方案
利用Gradle的**compileOnly**依赖方式，预先声明第三方API的空壳API实现，仿照定制API提供需要的空壳class、方法、属性等，提供给调用方。

### 声明空壳API module

!["图片自定义高度" height="" width=""](https://assets.che300.com/wiki/2022-07-19/16582107370664174.png)

**这里以MIUI的修改通知的角标API为例：**

```java
package android.app;

public class Notification {
    public MiuiNotification extraNotification;
}

public class MiuiNotification {
    public void setMessageCount(int messageCount) {
       // miui impl
    }
}
```
已知MIUI设置角标的API如上面代码块所示，那我们就在空壳module里声明`MiuiNotification`，方法体可以随便写，例如抛出异常，返回null等。因为只是对外提供API，保证编译通过，运行时调用的是系统的真正实现。

```kotlin
package android.app

/**
 * MIUI Notification Ext
 *
 * @since 2022/7/18
 */
class MiuiNotification {

    fun setMessageCount(messageCount: Int) {
        return this
    }
}
```
### 主module依赖 空壳API module

```kotlin
// 使用compleOnly方式，只提供API不参与打包
compileOnly(project(":systemApi"))
```

### 调用空壳API

```kotlin
val notification: Notification = builder.build()
try {
    // 由于extraNotification是在系统API上扩展的属性，无法通过空壳API来实现访问
    val field = Notification::class.java.getField("extraNotification")
    // 由于类声名完全一样可以直接进行强转
    val miuiNotification = field.get(notification) as MiuiNotification
    // 调用设置角标API
    miuiNotification.setMessageCount(10)
} catch (e: Throwable) {
    // 防止API变动导致异常
}
```

----

### 同理调用系统API

#### 通过ActivityThread获取应用上下文

```kotlin
try {
    val application = ActivityThread.currentActivityThread().application
    Log.i("TAG", "onViewCreated: " + application)
    Log.i("TAG", "onViewCreated: " + AppApplication.get())
} catch (e: Throwable) {
}
```
由于存在静态方法，推荐使用Java代码来声明空壳API
```java
package android.app;

/**
 * AcitivityThread API
 * @since 2022/7/19
 */
public class ActivityThread {

    public static ActivityThread currentActivityThread() {
        throw new UnsupportedOperationException();
    }

    public Application getApplication() {
        throw new UnsupportedOperationException();
    }
}
```
运行结果：
```log
2022-07-19 14:13:46.931 6546-6546/com.che300.internalapiaccess I/MainActivity: onCreate: com.che300.internalapiaccess.AppApplication@8aedd19
2022-07-19 14:13:46.931 6546-6546/com.che300.internalapiaccess I/MainActivity: onCreate: com.che300.internalapiaccess.AppApplication@8aedd19
```

#### Unsafe 实例化对象
```kotlin
try {
    // Unsafe getUnsafe 对类加载器进行了限制，必须系统类加载器才可以调用
    val method = Class.forName("sun.misc.Unsafe").getMethod("getUnsafe")
    method.isAccessible = true
    val unsafe = method.invoke(null) as Unsafe
            
    val any = unsafe.allocateInstance(Any::class.java)
    Log.i("TAG", "onViewCreated: " + any)
} catch (e: Throwable) {
}
```
空壳API
```java
package sun.misc;

/**
 * 空壳API
 *
 * @since 2022/7/19
 */
public class Unsafe {

    public Object allocateInstance(Class<?> clazz) throws InstantiationException {
        return null;
    }
}
```
运行结果
```log
2022-07-19 14:14:12.377 6546-6546/com.che300.internalapiaccess I/TAG: onCreate: java.lang.Object@dd3a8db
```

## 总结
这个方案可以减少一部分反射的调用，但是也有它的限制。同理可以利用这个思路对一些第三方SDK和系统非SDK API预声明进行访问，前提是要明确这些API的详细的声明，例如：访问修饰符、参数类型、方法返回值、属性类型。错误的声明会导致访问失败，所以必须配合**try-catch**一起使用。

## 参考文档
[小米开放平台](https://dev.mi.com/console/doc/)
[魅族开放平台](http://open-wiki.flyme.cn/doc-wiki/index#id?76)