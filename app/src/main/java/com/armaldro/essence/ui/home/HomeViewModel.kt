package com.armaldro.essence.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.armaldro.essence.EssenceApp
import com.armaldro.essence.data.AppEntry
import com.armaldro.essence.data.AppRepository
import com.armaldro.essence.data.FavoritesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val apps: List<AppEntry> = emptyList(),
    val favorites: List<AppEntry> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val query: String = "",
) {
    val filtered: List<AppEntry> =
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
}

class HomeViewModel(
    private val repository: AppRepository,
    private val favoritesStore: FavoritesStore,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<HomeState> =
        combine(repository.apps, favoritesStore.favorites, query) { apps, favKeys, q ->
            val byKey = apps.associateBy { it.key }
            HomeState(
                apps = apps,
                favorites = favKeys.mapNotNull { byKey[it] },
                favoriteKeys = favKeys.toSet(),
                query = q,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setQuery(value: String) { query.value = value }

    fun launch(app: AppEntry) = repository.launch(app)

    fun openDetails(app: AppEntry) = repository.openDetails(app)

    fun toggleFavorite(app: AppEntry) {
        viewModelScope.launch { favoritesStore.toggle(app.key) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EssenceApp
                HomeViewModel(app.apps, app.favorites)
            }
        }
    }
}
