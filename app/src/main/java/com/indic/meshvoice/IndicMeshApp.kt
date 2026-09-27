package com.indic.meshvoice

import android.app.Application
import com.indic.meshvoice.mesh.MeshEngine

class IndicMeshApp : Application() {

    lateinit var meshEngine: MeshEngine
        private set

    override fun onCreate() {
        super.onCreate()
        meshEngine = MeshEngine(this)
    }
}
