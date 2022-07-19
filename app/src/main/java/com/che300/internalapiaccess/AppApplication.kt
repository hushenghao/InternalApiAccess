package com.che300.internalapiaccess

import android.app.Application
import android.content.Context
import me.weishu.reflection.Reflection

/**
 * Created by shhu on 2022/7/19 11:58.
 *
 * @since 2022/7/19
 */
class AppApplication : Application() {

    companion object {

        private var instance: Application? = null

        fun get(): Application {
            return checkNotNull(instance)
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        Reflection.unseal(base)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}