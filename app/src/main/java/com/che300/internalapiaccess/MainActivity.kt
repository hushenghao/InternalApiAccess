package com.che300.internalapiaccess

import android.app.ActivityThread
import android.app.MiuiNotification
import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import sun.misc.Unsafe
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.bt_miui).setOnClickListener {
            val managerCompat = NotificationManagerCompat.from(this)
            val channel = NotificationChannelCompat.Builder("test",
                NotificationManagerCompat.IMPORTANCE_HIGH)
                .setShowBadge(true)
                .setVibrationEnabled(true)
                .setName("测试消息")
                .build()
            managerCompat.createNotificationChannel(channel)
            
            val notification = NotificationCompat.Builder(this, "test")
                .setContentTitle("测试通知")
                .setContentText("通知内容")
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .build()

            if (Build.MANUFACTURER.lowercase(Locale.ENGLISH) == "xiaomi") {
                try {
                    // 由于extraNotification是在系统API上扩展的属性，无法通过空壳API来实现访问
                    val field = Notification::class.java.getField("extraNotification")
                    // 由于类声名完全一样可以直接进行强转
                    val miuiNotification = field.get(notification) as MiuiNotification
                    // 调用设置角标API
                    miuiNotification.setMessageCount(10)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            managerCompat.notify(1, notification)
        }

        findViewById<Button>(R.id.bt_context).setOnClickListener {
            val application = ActivityThread.currentActivityThread().application
            Log.i(TAG, "onCreate: $application")
            Log.i(TAG, "onCreate: ${AppApplication.get()}")
        }

        findViewById<Button>(R.id.bt_unsafe).setOnClickListener {
            try {
                // Unsafe getUnsafe 对类加载器进行了限制，必须系统类加载器才可以调用
                val method = Class.forName("sun.misc.Unsafe").getMethod("getUnsafe")
                method.isAccessible = true
                val unsafe = method.invoke(null) as Unsafe

                val any = unsafe.allocateInstance(Any::class.java)
                Log.i(TAG, "onCreate: $any")
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}