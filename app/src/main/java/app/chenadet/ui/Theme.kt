package app.chenadet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.chenadet.core.ThermalState

@Composable
fun CheNadetTheme(thermalState: ThermalState? = null, darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val cold = thermalState in setOf(ThermalState.VERY_COLD, ThermalState.FREEZING, ThermalState.COLD)
    val colors = if (darkTheme) darkColorScheme(
        primary = if (cold) Color(0xFF91D1E8) else Color(0xFFF5CF75), onPrimary = Color(0xFF252016),
        primaryContainer = if (cold) Color(0xFF183E4C) else Color(0xFF483A1C), onPrimaryContainer = Color(0xFFFFF2D8),
        secondary = Color(0xFFD5C8AC), secondaryContainer = Color(0xFF37342D), onSecondaryContainer = Color(0xFFF4EEE2),
        background = Color(0xFF15191D), onBackground = Color(0xFFF3F0E9),
        surface = Color(0xFF1C2024), onSurface = Color(0xFFF3F0E9),
        surfaceVariant = Color(0xFF30363C), onSurfaceVariant = Color(0xFFD0D5DA),
    ) else lightColorScheme(
        primary = if (cold) Color(0xFF18586C) else Color(0xFF795500), onPrimary = Color.White,
        primaryContainer = if (cold) Color(0xFFD3ECF5) else Color(0xFFFFE29A), onPrimaryContainer = Color(0xFF282319),
        secondary = Color(0xFF62553D), secondaryContainer = Color(0xFFEDE7D9), onSecondaryContainer = Color(0xFF302B22),
        background = if (cold) Color(0xFFF3F8FB) else Color(0xFFFAF8F1), onBackground = Color(0xFF22272B),
        surface = Color(0xFFFFFEFA), onSurface = Color(0xFF22272B),
        surfaceVariant = Color(0xFFECEAE2), onSurfaceVariant = Color(0xFF474C50),
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
