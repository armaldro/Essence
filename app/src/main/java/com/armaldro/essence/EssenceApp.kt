package com.armaldro.essence

import android.app.Application
import com.armaldro.essence.agent.ClaudeAgent
import com.armaldro.essence.data.AgentStore
import com.armaldro.essence.data.AppRepository
import com.armaldro.essence.data.FavoritesStore

class EssenceApp : Application() {
    lateinit var apps: AppRepository
        private set
    lateinit var favorites: FavoritesStore
        private set
    lateinit var agentStore: AgentStore
        private set
    lateinit var agent: ClaudeAgent
        private set

    override fun onCreate() {
        super.onCreate()
        apps = AppRepository(this)
        favorites = FavoritesStore(this)
        agentStore = AgentStore(this)
        agent = ClaudeAgent(agentStore)
    }
}
