package com.roomhero.app.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.roomhero.app.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
