package com.armaldro.essence

import android.app.Application
import com.armaldro.essence.data.AppRepository
import com.armaldro.essence.data.FavoritesStore

class EssenceApp : Application() {
    lateinit var apps: AppRepository
        private set
    lateinit var favorites: FavoritesStore
        private set

    override fun onCreate() {
        super.onCreate()
        apps = AppRepository(this)
        favorites = FavoritesStore(this)
    }
}
