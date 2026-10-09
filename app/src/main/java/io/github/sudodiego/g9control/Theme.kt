package io.github.sudodiego.g9control

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF3DD6FF),
    onPrimary = Color(0xFF00222D),
    primaryContainer = Color(0xFF0D3A4A),
    onPrimaryContainer = Color(0xFFB9EEFF),
    secondary = Color(0xFF9AA8FF),
    onSecondary = Color(0xFF111A4D),
    secondaryContainer = Color(0xFF123B49),
    onSecondaryContainer = Color(0xFFBDEFFF),
    tertiary = Color(0xFFFFB86B),
    background = Color(0xFF0C0F14),
    onBackground = Color(0xFFE3E7ED),
    surface = Color(0xFF0C0F14),
    onSurface = Color(0xFFE3E7ED),
    surfaceVariant = Color(0xFF242B35),
    onSurfaceVariant = Color(0xFF9AA4B2),
    surfaceContainerLowest = Color(0xFF080A0E),
    surfaceContainerLow = Color(0xFF11151B),
    surfaceContainer = Color(0xFF151A21),
    surfaceContainerHigh = Color(0xFF1C222B),
    surfaceContainerHighest = Color(0xFF232A34),
    outline = Color(0xFF3A4452),
    outlineVariant = Color(0xFF2A313B),
    error = Color(0xFFFF6B6B),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00708F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDEBFA),
    onPrimaryContainer = Color(0xFF00303E),
    secondary = Color(0xFF4A5BC4),
    secondaryContainer = Color(0xFFD3F1FB),
    onSecondaryContainer = Color(0xFF003545),
    tertiary = Color(0xFFB0640A),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF14181E),
    surface = Color(0xFFF3F5F8),
    onSurface = Color(0xFF14181E),
    surfaceVariant = Color(0xFFDDE2E9),
    onSurfaceVariant = Color(0xFF515B68),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFECEFF3),
    surfaceContainerHighest = Color(0xFFE3E7EC),
    outline = Color(0xFFB4BCC7),
    outlineVariant = Color(0xFFD5DAE1),
)

enum class AppTheme { SYSTEM, LIGHT, DARK;
    val label get() = when (this) { SYSTEM -> tr("Del sistema", "System"); LIGHT -> tr("Claro", "Light"); DARK -> tr("Oscuro", "Dark") }
}

/** Tema elegido en Ajustes. */
object ThemeState {
    var theme by androidx.compose.runtime.mutableStateOf(AppTheme.SYSTEM)
}

@Composable
fun isDarkTheme() = when (ThemeState.theme) {
    AppTheme.SYSTEM -> isSystemInDarkTheme()
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
}

@Composable
fun G9Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDarkTheme()) DarkScheme else LightScheme,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content
    )
}
