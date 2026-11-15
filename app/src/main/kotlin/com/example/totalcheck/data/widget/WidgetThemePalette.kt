package com.example.totalcheck.data.widget

import android.content.Context
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.example.totalcheck.R
import com.example.totalcheck.data.preferences.ThemeConfig
import com.example.totalcheck.ui.theme.DarkOnPrimaryContainer
import com.example.totalcheck.ui.theme.DarkOnSurface
import com.example.totalcheck.ui.theme.DarkOnSurfaceVariant
import com.example.totalcheck.ui.theme.DarkOnTertiary
import com.example.totalcheck.ui.theme.DarkPrimary
import com.example.totalcheck.ui.theme.DarkTertiary
import com.example.totalcheck.ui.theme.LightOnPrimaryContainer
import com.example.totalcheck.ui.theme.LightOnSurface
import com.example.totalcheck.ui.theme.LightOnSurfaceVariant
import com.example.totalcheck.ui.theme.LightOnTertiary
import com.example.totalcheck.ui.theme.LightPrimary
import com.example.totalcheck.ui.theme.LightTertiary

enum class WidgetThemeVariant {
    CURATED_DARK,
    CURATED_LIGHT,
    DYNAMIC_DARK,
    DYNAMIC_LIGHT,
}

data class WidgetThemeStyle(
    val variant: WidgetThemeVariant,
    @DrawableRes val containerBgRes: Int,
    @DrawableRes val tileUncheckedBgRes: Int,
    @DrawableRes val tileCheckedBgRes: Int,
    @DrawableRes val dotIconRes: Int,
    @DrawableRes val checkIconRes: Int,
    @DrawableRes val buttonPillBgRes: Int,
    @DrawableRes val primaryPillBgRes: Int,
    @DrawableRes val tertiaryPillBgRes: Int,
    @DrawableRes val surfacePillBgRes: Int,
    @DrawableRes val errorPillBgRes: Int,
    @ColorInt val textPrimary: Int,
    @ColorInt val textMuted: Int,
    @ColorInt val accentPrimary: Int,
    @ColorInt val accentTertiary: Int,
    @ColorInt val onPrimaryContainer: Int,
    @ColorInt val onTertiary: Int,
    @ColorInt val onErrorContainer: Int,
)

private const val CURATED_ON_ERROR_DARK_CONTAINER = 0xFFFFDAD6.toInt()
private const val CURATED_ON_ERROR_LIGHT_CONTAINER = 0xFF410E0B.toInt()

fun resolveWidgetStyle(
    context: Context,
    themeConfig: ThemeConfig,
): WidgetThemeStyle {
    if (themeConfig.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val scheme = if (themeConfig.isDarkMode) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        return if (themeConfig.isDarkMode) {
            WidgetThemeStyle(
                variant = WidgetThemeVariant.DYNAMIC_DARK,
                containerBgRes = R.drawable.widget_bg_dynamic_dark,
                tileUncheckedBgRes = R.drawable.widget_tile_unchecked_dynamic_dark,
                tileCheckedBgRes = R.drawable.widget_tile_checked_dynamic_dark,
                dotIconRes = R.drawable.widget_ic_dot_dynamic_dark,
                checkIconRes = R.drawable.widget_ic_check_dynamic_dark,
                buttonPillBgRes = R.drawable.widget_pill_btn_dynamic_dark,
                primaryPillBgRes = R.drawable.widget_pill_primary_dynamic_dark,
                tertiaryPillBgRes = R.drawable.widget_pill_tertiary_dynamic_dark,
                surfacePillBgRes = R.drawable.widget_pill_surface_dynamic_dark,
                errorPillBgRes = R.drawable.widget_pill_error_dark,
                textPrimary = scheme.onSurface.toArgb(),
                textMuted = scheme.onSurfaceVariant.toArgb(),
                accentPrimary = scheme.primary.toArgb(),
                accentTertiary = scheme.tertiary.toArgb(),
                onPrimaryContainer = scheme.onPrimaryContainer.toArgb(),
                onTertiary = scheme.onTertiary.toArgb(),
                onErrorContainer = CURATED_ON_ERROR_DARK_CONTAINER,
            )
        } else {
            WidgetThemeStyle(
                variant = WidgetThemeVariant.DYNAMIC_LIGHT,
                containerBgRes = R.drawable.widget_bg_dynamic_light,
                tileUncheckedBgRes = R.drawable.widget_tile_unchecked_dynamic_light,
                tileCheckedBgRes = R.drawable.widget_tile_checked_dynamic_light,
                dotIconRes = R.drawable.widget_ic_dot_dynamic_light,
                checkIconRes = R.drawable.widget_ic_check_dynamic_light,
                buttonPillBgRes = R.drawable.widget_pill_btn_dynamic_light,
                primaryPillBgRes = R.drawable.widget_pill_primary_dynamic_light,
                tertiaryPillBgRes = R.drawable.widget_pill_tertiary_dynamic_light,
                surfacePillBgRes = R.drawable.widget_pill_surface_dynamic_light,
                errorPillBgRes = R.drawable.widget_pill_error_light,
                textPrimary = scheme.onSurface.toArgb(),
                textMuted = scheme.onSurfaceVariant.toArgb(),
                accentPrimary = scheme.primary.toArgb(),
                accentTertiary = scheme.tertiary.toArgb(),
                onPrimaryContainer = scheme.onPrimaryContainer.toArgb(),
                onTertiary = scheme.onTertiary.toArgb(),
                onErrorContainer = CURATED_ON_ERROR_LIGHT_CONTAINER,
            )
        }
    }

    return if (themeConfig.isDarkMode) {
        WidgetThemeStyle(
            variant = WidgetThemeVariant.CURATED_DARK,
            containerBgRes = R.drawable.widget_bg_curated_dark,
            tileUncheckedBgRes = R.drawable.widget_tile_unchecked_dark,
            tileCheckedBgRes = R.drawable.widget_tile_checked_dark,
            dotIconRes = R.drawable.widget_ic_dot_curated_dark,
            checkIconRes = R.drawable.widget_ic_check_curated_dark,
            buttonPillBgRes = R.drawable.widget_pill_btn_dark,
            primaryPillBgRes = R.drawable.widget_pill_primary_dark,
            tertiaryPillBgRes = R.drawable.widget_pill_tertiary_dark,
            surfacePillBgRes = R.drawable.widget_pill_surface_dark,
            errorPillBgRes = R.drawable.widget_pill_error_dark,
            textPrimary = DarkOnSurface.toArgb(),
            textMuted = DarkOnSurfaceVariant.toArgb(),
            accentPrimary = DarkPrimary.toArgb(),
            accentTertiary = DarkTertiary.toArgb(),
            onPrimaryContainer = DarkOnPrimaryContainer.toArgb(),
            onTertiary = DarkOnTertiary.toArgb(),
            onErrorContainer = CURATED_ON_ERROR_DARK_CONTAINER,
        )
    } else {
        WidgetThemeStyle(
            variant = WidgetThemeVariant.CURATED_LIGHT,
            containerBgRes = R.drawable.widget_bg_curated_light,
            tileUncheckedBgRes = R.drawable.widget_tile_unchecked_light,
            tileCheckedBgRes = R.drawable.widget_tile_checked_light,
            dotIconRes = R.drawable.widget_ic_dot_curated_light,
            checkIconRes = R.drawable.widget_ic_check_curated_light,
            buttonPillBgRes = R.drawable.widget_pill_btn_light,
            primaryPillBgRes = R.drawable.widget_pill_primary_light,
            tertiaryPillBgRes = R.drawable.widget_pill_tertiary_light,
            surfacePillBgRes = R.drawable.widget_pill_surface_light,
            errorPillBgRes = R.drawable.widget_pill_error_light,
            textPrimary = LightOnSurface.toArgb(),
            textMuted = LightOnSurfaceVariant.toArgb(),
            accentPrimary = LightPrimary.toArgb(),
            accentTertiary = LightTertiary.toArgb(),
            onPrimaryContainer = LightOnPrimaryContainer.toArgb(),
            onTertiary = LightOnTertiary.toArgb(),
            onErrorContainer = CURATED_ON_ERROR_LIGHT_CONTAINER,
        )
    }
}
