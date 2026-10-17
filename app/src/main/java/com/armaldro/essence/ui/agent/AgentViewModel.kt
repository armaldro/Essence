package com.armaldro.essence.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anthropic.models.messages.OutputConfig
import com.armaldro.essence.EssenceApp
import com.armaldro.essence.agent.ClaudeAgent
import com.armaldro.essence.data.AgentCard
import com.armaldro.essence.data.AgentStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Agent cards, memories and Claude settings for the home and settings screens. */
class AgentViewModel(private val store: AgentStore, private val agent: ClaudeAgent) : ViewModel() {
    val cards = store.cards.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val memories = store.memories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hasApiKey = store.hasApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val model = store.model.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    /** IDs of cards whose agent is running right now. */
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    fun refresh(card: AgentCard) {
        if (card.id in _refreshing.value) return
        _refreshing.update { it + card.id }
        viewModelScope.launch {
            val now = ZonedDateTime.now()
            val task = "It is ${now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm (z)"))}.\n\n${card.prompt}"
            val output = runCatching {
                agent.run(task = task, instructions = CARD_INSTRUCTIONS, effort = OutputConfig.Effort.LOW)
            }.getOrElse { "⚠ ${it.message ?: "Couldn't reach Claude."}" }
            store.saveCard(card.copy(output = output, updatedAt = System.currentTimeMillis()))
            _refreshing.update { it - card.id }
        }
    }

    fun saveCard(card: AgentCard, refreshAfter: Boolean) {
        viewModelScope.launch {
            store.saveCard(card)
            if (refreshAfter) refresh(card)
        }
    }

    fun deleteCard(id: String) = viewModelScope.launch { store.deleteCard(id) }
    fun setApiKey(value: String) = viewModelScope.launch { store.setApiKey(value) }
    fun setModel(value: String) = viewModelScope.launch { store.setModel(value) }
    fun addMemory(text: String) = viewModelScope.launch { if (text.isNotBlank()) store.addMemory(text) }
    fun deleteMemory(id: String) = viewModelScope.launch { store.deleteMemory(id) }

    companion object {
        private val CARD_INSTRUCTIONS = """
            You are Essence, an assistant that lives on the user's phone home screen. Each request comes from a
            card on the home screen, and your answer is printed on that card, which looks like an e-ink display.
            Answer in plain text with no Markdown: no headings, bold, tables or bullet symbols. Keep it short
            enough to read at a glance, usually under 80 words; use short lines when listing things. Use what you
            remember about the user to make the answer personal.
        """.trimIndent()

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EssenceApp
                AgentViewModel(app.agentStore, app.agent)
            }
        }
    }
}
