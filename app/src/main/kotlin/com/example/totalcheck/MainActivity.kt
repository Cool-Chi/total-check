package com.example.totalcheck

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.totalcheck.ui.main.MainViewModel
import com.example.totalcheck.ui.main.TotalCheckScreen
import com.example.totalcheck.ui.theme.TotalCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val isDarkMode by mainViewModel.isDarkMode.collectAsStateWithLifecycle()
            val useDynamicColor by mainViewModel.useDynamicColor.collectAsStateWithLifecycle()

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !isDarkMode
                    insetsController.isAppearanceLightNavigationBars = !isDarkMode
                }
            }

            TotalCheckTheme(
                darkTheme = isDarkMode,
                dynamicColor = useDynamicColor
            ) {
                TotalCheckScreen(viewModel = mainViewModel)
            }
        }
    }
}
