package com.voiceasistanttv.stt

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Lang.init(this)
        Config.init(this)
    }
}
