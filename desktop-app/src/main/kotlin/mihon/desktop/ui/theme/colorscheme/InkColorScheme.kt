package mihon.desktop.ui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * MihonDesk brand palette (0.3.0), matched to the approved splash v3: warm paper with a terracotta
 * ink drop. Splash tokens: paper #F6F1E7 / #1B1A19, ink #2B2622 / #E9E3D8, accent #B0623B / #D98A5F.
 * The light primary is darkened to #9A4F2B so text and icons on paper keep WCAG AA (≥ 4.5:1).
 */
internal object InkColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFD98A5F),
        onPrimary = Color(0xFF4A2210),
        primaryContainer = Color(0xFF6E3517),
        onPrimaryContainer = Color(0xFFFFDBCB),
        inversePrimary = Color(0xFF9A4F2B),
        secondary = Color(0xFFE7BEAD),
        onSecondary = Color(0xFF442A1E),
        secondaryContainer = Color(0xFF5D4033),
        onSecondaryContainer = Color(0xFFFFDBCC),
        tertiary = Color(0xFFCDC991),
        onTertiary = Color(0xFF343208),
        tertiaryContainer = Color(0xFF4B481D),
        onTertiaryContainer = Color(0xFFEAE5AB),
        background = Color(0xFF1B1A19),
        onBackground = Color(0xFFE9E3D8),
        surface = Color(0xFF1B1A19),
        onSurface = Color(0xFFE9E3D8),
        surfaceVariant = Color(0xFF262422),
        onSurfaceVariant = Color(0xFFD7C2B8),
        surfaceTint = Color(0xFFD98A5F),
        inverseSurface = Color(0xFFE9E3D8),
        inverseOnSurface = Color(0xFF1B1A19),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFFA08D84),
        outlineVariant = Color(0xFF52443C),
        surfaceContainerLowest = Color(0xFF141312),
        surfaceContainerLow = Color(0xFF1F1D1C),
        surfaceContainer = Color(0xFF23211F),
        surfaceContainerHigh = Color(0xFF2D2A28),
        surfaceContainerHighest = Color(0xFF383432),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF9A4F2B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDBCB),
        onPrimaryContainer = Color(0xFF3A1606),
        inversePrimary = Color(0xFFD98A5F),
        secondary = Color(0xFF775849),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF6DDD0),
        onSecondaryContainer = Color(0xFF2C160B),
        tertiary = Color(0xFF636032),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFEAE5AB),
        onTertiaryContainer = Color(0xFF1E1C00),
        background = Color(0xFFF6F1E7),
        onBackground = Color(0xFF2B2622),
        surface = Color(0xFFF6F1E7),
        onSurface = Color(0xFF2B2622),
        surfaceVariant = Color(0xFFF0E6DA),
        onSurfaceVariant = Color(0xFF52443C),
        surfaceTint = Color(0xFF9A4F2B),
        inverseSurface = Color(0xFF352F2B),
        inverseOnSurface = Color(0xFFFAEFE6),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF85746B),
        outlineVariant = Color(0xFFD7C2B8),
        surfaceContainerLowest = Color(0xFFFFFAF1),
        surfaceContainerLow = Color(0xFFF9F3EA),
        surfaceContainer = Color(0xFFF2EBE0),
        surfaceContainerHigh = Color(0xFFEDE5D9),
        surfaceContainerHighest = Color(0xFFE7DFD3),
    )
}
