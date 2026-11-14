package app.pquest

import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pquest.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A numeric keypad with glove-sized keys. Calls [onDone] with a PIN of 4 to 8 digits. */
@Composable
fun PinPad(title: String, error: String?, busy: Boolean, onDone: (String) -> Unit) {
    var pin by remember(title) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(if (busy) "…" else "●".repeat(pin.length).ifEmpty { "—" }, fontSize = 30.sp, letterSpacing = 6.sp)
        Text(error ?: " ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        // Digits keep the same positions in both languages.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            for (row in listOf("123", "456", "789", "<0>")) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (k in row) Big(
                    when (k) { '<' -> "⌫"; '>' -> "OK"; else -> k.toString() }, Modifier.width(92.dp).height(64.dp),
                    enabled = !busy && (k != '>' || pin.length >= 4), filled = k == '>',
                ) {
                    when (k) { '<' -> pin = pin.dropLast(1); '>' -> { onDone(pin); pin = "" }; else -> if (pin.length < 8) pin += k }
                }
            }
        }
    }
}

/**
 * Shown on launch and after five minutes in the background. Creates the PIN on first run.
 * Holding the title for three seconds is the hidden panic-wipe gesture.
 */
@Composable
fun LockScreen(app: App, activity: ComponentActivity, onUnlocked: (Vault.Unlocked) -> Unit) {
    val scope = rememberCoroutineScope()
    val creating = remember { !Vault.hasPin(app) }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val mismatch = stringResource(R.string.pin_mismatch); val wrong = stringResource(R.string.pin_wrong)
    val left = stringResource(R.string.pin_attempts_left)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                stringResource(R.string.app_name), fontSize = 34.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures(onPress = { val t = scope.launch { delay(3000); app.wipe() }; tryAwaitRelease(); t.cancel() }) },
            )
            Spacer(Modifier.height(20.dp))
            PinPad(
                stringResource(when { !creating -> R.string.pin_enter; first == null -> R.string.pin_create; else -> R.string.pin_repeat }), error, busy,
            ) { pin ->
                error = null
                if (creating) {
                    if (first == null) first = pin
                    else if (first != pin) { first = null; error = mismatch }
                    else { busy = true; scope.launch { val u = withContext(Dispatchers.Default) { Vault.create(app, pin) }; busy = false; onUnlocked(u) } }
                } else {
                    busy = true
                    scope.launch {
                        val u = withContext(Dispatchers.Default) { Vault.unlock(app, pin) }
                        busy = false
                        if (u != null) onUnlocked(u)
                        else if (Vault.failures(app) >= Vault.MAX_ATTEMPTS) app.wipe()
                        else error = wrong + " " + left.format(Vault.MAX_ATTEMPTS - Vault.failures(app))
                    }
                }
            }
            if (!creating && Vault.hasBiometric(app)) {
                Spacer(Modifier.height(12.dp))
                Big(stringResource(R.string.use_biometric)) { biometric(activity, Vault.biometricDecryptCipher(app)) { c -> Vault.openBiometric(app, c)?.let(onUnlocked) } }
            }
        }
    }
}

/** Runs the system biometric prompt around a Keystore cipher that only works after a successful check. */
fun biometric(activity: ComponentActivity, cipher: javax.crypto.Cipher?, onOk: (javax.crypto.Cipher) -> Unit) {
    if (cipher == null) return
    runCatching {
        BiometricPrompt.Builder(activity).setTitle(activity.getString(R.string.app_name))
            .setNegativeButton(activity.getString(android.R.string.cancel), activity.mainExecutor) { _, _ -> }.build()
            .authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) { r.cryptoObject?.cipher?.let(onOk) }
            })
    }
}

/** A button that only fires after being held for three seconds (emergency alert, wipe). */
@Composable
fun HoldButton(text: String, modifier: Modifier = Modifier, onHeld: () -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.heightIn(min = 58.dp).pointerInput(Unit) {
            detectTapGestures(onPress = {
                val job = scope.launch { for (i in 1..30) { delay(100); progress = i / 30f }; progress = 0f; onHeld() }
                tryAwaitRelease(); job.cancel(); progress = 0f
            })
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), color = cs.error, contentColor = cs.onError, shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (progress > 0) LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(8.dp))
            Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 13.sp, lineHeight = 15.sp)
        }
    }
}
