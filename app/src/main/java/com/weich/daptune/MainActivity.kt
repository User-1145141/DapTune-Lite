package com.weich.daptune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.weich.daptune.core.designsystem.DapTuneTheme
import com.weich.daptune.feature.automation.AutomationRecoveryScheduler
import com.weich.daptune.feature.automation.AutomationScreen
import com.weich.daptune.feature.editor.EditorScreen
import com.weich.daptune.feature.profiles.ProfilesScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    @Inject lateinit var recoveryScheduler: AutomationRecoveryScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DapTuneTheme {
                LaunchedEffect(Unit) { appViewModel.restoreAutomation() }
                DapTuneApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        recoveryScheduler.disarm()
    }

    override fun onPause() {
        recoveryScheduler.arm()
        super.onPause()
    }
}

private enum class AppDestination(
    val label: String,
    val outlinedIcon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Editor("调音", Icons.Outlined.Equalizer, Icons.Rounded.Equalizer),
    Profiles("配置", Icons.Outlined.Tune, Icons.Rounded.Tune),
    Automation(
        "自动",
        Icons.Outlined.AutoAwesomeMotion,
        Icons.Rounded.AutoAwesomeMotion,
    ),
}

@Composable
private fun DapTuneApp() {
    val destinations = AppDestination.entries
    val pagerState = rememberPagerState(pageCount = { destinations.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                destinations.forEachIndexed { index, destination ->
                    val selected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        icon = {
                            Icon(
                                if (selected) destination.selectedIcon else destination.outlinedIcon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            beyondViewportPageCount = 1,
            key = { destinations[it].name },
        ) {
            when (destinations[it]) {
                AppDestination.Editor -> EditorScreen()
                AppDestination.Profiles -> ProfilesScreen(isActive = pagerState.currentPage == it)
                AppDestination.Automation -> AutomationScreen()
            }
        }
    }
}
