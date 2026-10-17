package com.armaldro.essence

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.armaldro.essence.ui.agent.AgentViewModel
import com.armaldro.essence.ui.home.HomeViewModel
import com.armaldro.essence.ui.home.LauncherRoot
import com.armaldro.essence.ui.home.Page
import com.armaldro.essence.ui.theme.EssenceTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory }
    private val agentViewModel: AgentViewModel by viewModels { AgentViewModel.Factory }
    private var page by mutableStateOf(Page.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EssenceTheme {
                LauncherRoot(
                    viewModel = viewModel,
                    agentViewModel = agentViewModel,
                    page = page,
                    onPageChange = { page = it },
                )
            }
        }
    }

    // Pressing Home while the launcher is showing returns to the home page.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.setQuery("")
            page = Page.Home
        }
    }
}
