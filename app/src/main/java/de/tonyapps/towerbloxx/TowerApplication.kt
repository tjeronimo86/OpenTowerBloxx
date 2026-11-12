package de.tonyapps.towerbloxx

import android.app.Application
import de.tonyapps.towerbloxx.audio.Audio
import de.tonyapps.towerbloxx.data.AppContext
import de.tonyapps.towerbloxx.data.Store

class TowerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.app = this
        Store.init(this)
        Audio.soundOn = Store.sound
        Audio.vibrationOn = Store.vibration
        Audio.musicOn = Store.music
        Audio.init(this)
    }
}
