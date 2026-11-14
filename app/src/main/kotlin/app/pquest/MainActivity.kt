package app.pquest

import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
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

/** Urdu is set in a bundled Nastaliq face, which needs more line height than Latin text. */
private val Nastaliq = FontFamily(Font(R.font.nastaliq))
private fun urdu(base: Typography): Typography {
    fun TextStyle.ur() = copy(fontFamily = Nastaliq, lineHeight = 1.9.em)
    return base.copy(
        bodyLarge = base.bodyLarge.ur(), bodyMedium = base.bodyMedium.ur(), bodySmall = base.bodySmall.ur(),
        labelLarge = base.labelLarge.ur(), labelMedium = base.labelMedium.ur(), labelSmall = base.labelSmall.ur(),
        titleLarge = base.titleLarge.ur(), titleMedium = base.titleMedium.ur(), titleSmall = base.titleSmall.ur(),
    )
}

class MainActivity : ComponentActivity() {
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App
        runCatching { java.io.File(cacheDir, "share").deleteRecursively() } // exported files do not linger
        setContent {
            // Screenshots and the recent-apps preview are blocked unless an admin allows them.
            val secure = app.settings.secure
            LaunchedEffect(secure) { if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            // Language switches instantly: strings and layout direction come from a locale-specific context.
            val lang = app.settings.lang
            val localized = remember(lang) {
                createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale(lang)) })
            }
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalLayoutDirection provides if (lang == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MaterialTheme(colorScheme = if (app.settings.night) Night else Day, typography = if (lang == "ur") urdu(Typography()) else Typography()) {
                    val session = app.session
                    if (session == null || app.locked) LockScreen(app, this) { u ->
                        // Re-entering the same profile's PIN just unlocks; the other profile's PIN switches to it.
                        if (session == null || session.duress != u.duress) app.open(u)
                        app.locked = false
                    } else MapScreen(app, this, session)
                }
            }
        }
    }

    override fun onStop() { super.onStop(); stoppedAt = System.currentTimeMillis() }

    /** App lock: the PIN is asked again after five minutes in the background. Tracking and reporting keep running. */
    override fun onStart() {
        super.onStart()
        val app = application as App
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 5 * 60_000 && app.session != null) app.locked = true
    }
}
