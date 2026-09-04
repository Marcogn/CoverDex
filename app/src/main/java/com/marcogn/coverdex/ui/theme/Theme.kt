package com.marcogn.coverdex.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary40,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandPrimaryContainer90,
    onPrimaryContainer = BrandOnPrimaryContainer10,
    secondary = BrandSecondary40,
    tertiary = BrandTertiary40,
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimary80,
    onPrimary = BrandOnPrimaryDark,
    primaryContainer = BrandPrimaryContainer30,
    onPrimaryContainer = BrandOnPrimaryContainer90,
    secondary = BrandSecondary80,
    tertiary = BrandTertiary80,
)

@Composable
fun CoverDexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
