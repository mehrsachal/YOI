package app.pquest

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/** High-contrast day palette for direct sunlight. */
private val Day = lightColorScheme(
    primary = Color(0xFF00337A), onPrimary = Color.White, surface = Color.White, onSurface = Color.Black,
    background = Color.White, onBackground = Color.Black, surfaceVariant = Color(0xFFE4E4E4), onSurfaceVariant = Color.Black,
    secondaryContainer = Color(0xFFD5E2F7), onSecondaryContainer = Color.Black, outline = Color.Black, error = Color(0xFFB00000),
)

/** Red-on-black night palette that preserves dark adaptation. */
private val Red = Color(0xFFFF4136)
private val Night = darkColorScheme(
    primary = Red, onPrimary = Color.Black, surface = Color.Black, onSurface = Red, background = Color.Black, onBackground = Red,
    surfaceVariant = Color(0xFF260000), onSurfaceVariant = Red, secondaryContainer = Color(0xFF4A0000), onSecondaryContainer = Red,
    outline = Color(0xFF9A1A12), error = Color(0xFFFF8A80), onError = Color.Black,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App
        setContent {
            // Language switches instantly: strings and layout direction come from a locale-specific context.
            val lang = app.settings.lang
            val localized = remember(lang) {
                createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale(lang)) })
            }
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalLayoutDirection provides if (lang == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MaterialTheme(colorScheme = if (app.settings.night) Night else Day) { MapScreen(app, this) }
            }
        }
    }
}
