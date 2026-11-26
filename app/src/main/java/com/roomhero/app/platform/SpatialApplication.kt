package com.roomhero.app.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.roomhero.app.mainApp
import com.roomhero.app.manager.FeedbackManager
import com.roomhero.app.manager.FurnitureAnchorManager
import com.roomhero.app.network.TripoClient

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FeedbackManager.init(this)
        FurnitureAnchorManager.init(this)
        TripoClient.init(this)
        launch(::mainApp)
    }
}
