package com.example.totalcheck

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.totalcheck.ui.main.MainViewModel
import com.example.totalcheck.ui.main.TotalCheckScreen
import com.example.totalcheck.ui.theme.RadialThemeTransitionContainer
import com.example.totalcheck.ui.theme.TotalCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val themeConfig by mainViewModel.themeConfig.collectAsStateWithLifecycle()
            val currentTheme = themeConfig ?: return@setContent

            DisposableEffect(currentTheme.isDarkMode) {
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
                onDispose {}
            }

            TotalCheckTheme(
                darkTheme = currentTheme.isDarkMode,
                dynamicColor = currentTheme.useDynamicColor
            ) {
                RadialThemeTransitionContainer(
                    onToggleTheme = { mainViewModel.toggleDarkMode() }
                ) { triggerTransition, isTransitioning ->
                    TotalCheckScreen(
                        viewModel = mainViewModel,
                        onToggleTheme = triggerTransition,
                        isThemeTransitioning = isTransitioning
                    )
                }
            }
        }
    }
}
