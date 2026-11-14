package com.example.totalcheck

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.totalcheck.ui.main.MainViewModel
import com.example.totalcheck.ui.main.TotalCheckScreen
import com.example.totalcheck.ui.theme.RadialThemeTransitionContainer
import com.example.totalcheck.ui.theme.TotalCheckTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWidgetIntent(intent)
        setContent {
            val themeConfig by mainViewModel.themeConfig.collectAsStateWithLifecycle()
            val currentTheme = themeConfig ?: return@setContent

            TotalCheckTheme(
                darkTheme = currentTheme.isDarkMode,
                dynamicColor = currentTheme.useDynamicColor
            ) {
                RadialThemeTransitionContainer(
                    onToggleTheme = { mainViewModel.toggleDarkMode() }
                ) { triggerTransition, onPreCapture, onCancelPreCapture, isTransitioning ->
                    LaunchedEffect(currentTheme.isDarkMode, isTransitioning) {
                        if (!isTransitioning) {
                            enableEdgeToEdge(
                                statusBarStyle = if (currentTheme.isDarkMode) {
                                    SystemBarStyle.dark(Color.TRANSPARENT)
                                } else {
                                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                                },
                                navigationBarStyle = if (currentTheme.isDarkMode) {
                                    SystemBarStyle.dark(Color.TRANSPARENT)
                                } else {
                                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                                }
                            )
                        }
                    }
                    TotalCheckScreen(
                        viewModel = mainViewModel,
                        onToggleTheme = triggerTransition,
                        onPreCaptureTheme = onPreCapture,
                        onCancelPreCaptureTheme = onCancelPreCapture,
                        isThemeTransitioning = isTransitioning
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null || !intent.hasExtra(EXTRA_WIDGET_CHECKLIST_ID)) return
        val checklistId = intent.getLongExtra(EXTRA_WIDGET_CHECKLIST_ID, -1L)
        if (checklistId > 0L) {
            mainViewModel.selectAndScrollToChecklist(checklistId)
            intent.removeExtra(EXTRA_WIDGET_CHECKLIST_ID)
        }
    }

    companion object {
        const val EXTRA_WIDGET_CHECKLIST_ID = "extra_widget_checklist_id"
    }
}
