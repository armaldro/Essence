package com.armaldro.essence.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.Collator

/** Reads launchable apps for every profile and re-emits whenever packages change. */
class AppRepository(context: Context) {
    private val ownPackage = context.packageName
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    val apps: Flow<List<AppEntry>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) { trySend(load()) }
            override fun onPackageAdded(packageName: String, user: UserHandle) { trySend(load()) }
            override fun onPackageChanged(packageName: String, user: UserHandle) { trySend(load()) }
            override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(load()) }
            override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(load()) }
            override fun onPackagesSuspended(names: Array<out String>, user: UserHandle, extras: Bundle?) { trySend(load()) }
            override fun onPackagesUnsuspended(names: Array<out String>, user: UserHandle) { trySend(load()) }
        }
        trySend(load())
        launcherApps.registerCallback(callback)
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    private fun load(): List<AppEntry> =
        userManager.userProfiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            .filter { it.applicationInfo.packageName != ownPackage }
            .map { AppEntry(it.label.toString().trim(), it.componentName, it.user) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }

    fun launch(app: AppEntry) {
        launcherApps.startMainActivity(app.component, app.user, null, null)
    }

    fun openDetails(app: AppEntry) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }
}
