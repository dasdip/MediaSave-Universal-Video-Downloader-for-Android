package com.materialtube.downloader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.materialtube.downloader.data.ThemeMode

data class SeedPreset(
    val name: String,
    val primaryLight: Color,
    val primaryContainerLight: Color,
    val secondaryContainerLight: Color,
    val primaryDark: Color,
    val primaryContainerDark: Color,
    val secondaryContainerDark: Color,
)

val MaterialYouPresets = listOf(
    SeedPreset(
        name = "Pixel Blue",
        primaryLight = Color(0xFF0B57D0),
        primaryContainerLight = Color(0xFFD3E3FD),
        secondaryContainerLight = Color(0xFFC2E7FF),
        primaryDark = Color(0xFFA8C7FA),
        primaryContainerDark = Color(0xFF0842A0),
        secondaryContainerDark = Color(0xFF004A77),
    ),
    SeedPreset(
        name = "Lavender",
        primaryLight = Color(0xFF6750A4),
        primaryContainerLight = Color(0xFFEADDFF),
        secondaryContainerLight = Color(0xFFE8DEF8),
        primaryDark = Color(0xFFD0BCFF),
        primaryContainerDark = Color(0xFF4F378B),
        secondaryContainerDark = Color(0xFF4A4458),
    ),
    SeedPreset(
        name = "Sage Green",
        primaryLight = Color(0xFF2E6B4F),
        primaryContainerLight = Color(0xFFB3F1CF),
        secondaryContainerLight = Color(0xFFCFE9D9),
        primaryDark = Color(0xFF97D4B4),
        primaryContainerDark = Color(0xFF135138),
        secondaryContainerDark = Color(0xFF354B40),
    ),
    SeedPreset(
        name = "Terracotta",
        primaryLight = Color(0xFFB33B24),
        primaryContainerLight = Color(0xFFFFDAD2),
        secondaryContainerLight = Color(0xFFFFDBD3),
        primaryDark = Color(0xFFFFB4A4),
        primaryContainerDark = Color(0xFF8F2410),
        secondaryContainerDark = Color(0xFF5D3F39),
    ),
    SeedPreset(
        name = "Ocean Teal",
        primaryLight = Color(0xFF006874),
        primaryContainerLight = Color(0xFF9EEFFD),
        secondaryContainerLight = Color(0xFFCDE7EC),
        primaryDark = Color(0xFF82D3E0),
        primaryContainerDark = Color(0xFF004F58),
        secondaryContainerDark = Color(0xFF334A4F),
    ),
)

private fun presetColorScheme(preset: SeedPreset, darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        darkColorScheme(
            primary = preset.primaryDark,
            primaryContainer = preset.primaryContainerDark,
            secondaryContainer = preset.secondaryContainerDark,
            surface = Color(0xFF111318),
            surfaceContainerLowest = Color(0xFF0C0E13),
            surfaceContainerLow = Color(0xFF191C21),
            surfaceContainer = Color(0xFF1D2025),
            surfaceContainerHigh = Color(0xFF282A30),
            surfaceContainerHighest = Color(0xFF33353B),
        )
    } else {
        lightColorScheme(
            primary = preset.primaryLight,
            primaryContainer = preset.primaryContainerLight,
            secondaryContainer = preset.secondaryContainerLight,
            surface = Color(0xFFF9F9FF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF3F3FA),
            surfaceContainer = Color(0xFFEDEDF4),
            surfaceContainerHigh = Color(0xFFE7E8EE),
            surfaceContainerHighest = Color(0xFFE1E2E9),
        )
    }
}

private val ExpressiveShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MaterialTubeTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    seedPresetIndex: Int,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val preset = MaterialYouPresets.getOrElse(seedPresetIndex) { MaterialYouPresets[0] }
        presetColorScheme(preset, darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        content = content,
    )
}
