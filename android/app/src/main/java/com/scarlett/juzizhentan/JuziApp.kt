package com.scarlett.juzizhentan

import android.app.Application
import com.scarlett.juzizhentan.data.AppContainer

class JuziApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
