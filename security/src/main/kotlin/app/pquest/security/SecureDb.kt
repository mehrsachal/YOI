package app.pquest.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** PIN-derived wrapping of a random database passphrase. Pure JVM crypto, so it is unit-tested off-device. */
object PinCrypto {
    const val ITERATIONS = 120_000
    private val rnd = SecureRandom()

    fun salt(): ByteArray = ByteArray(16).also { rnd.nextBytes(it) }
    fun random(n: Int): ByteArray = ByteArray(n).also { rnd.nextBytes(it) }

    fun derive(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)).encoded

    fun wrap(pin: String, salt: ByteArray, secret: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derive(pin, salt), "AES"))
        return c.iv + c.doFinal(secret)
    }

    /** Returns null when the PIN is wrong (the GCM tag does not verify). */
    fun unwrap(pin: String, salt: ByteArray, blob: ByteArray): ByteArray? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(derive(pin, salt), "AES"), GCMParameterSpec(128, blob, 0, 12))
        c.doFinal(blob, 12, blob.size - 12)
    }.getOrNull()
}

/**
 * Holds the database passphrase so that it can only be recovered with the user's PIN *and* this device's
 * Keystore: PIN-derived AES-GCM inside, a non-exportable Android Keystore key outside.
 * A second, duress PIN unlocks a separate empty profile.
 */
object Vault {
    class Unlocked(val passphrase: ByteArray, val duress: Boolean)

    const val MAX_ATTEMPTS = 10
    private const val ALIAS = "pquest_master"
    private const val BIO_ALIAS = "pquest_bio"
    private const val PREF = "vault"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun b64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun deviceKey(alias: String = ALIAS, userAuth: Boolean = false): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).setUserAuthenticationRequired(userAuth).build()
            )
            generateKey()
        }
    }

    private fun seal(plain: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.ENCRYPT_MODE, deviceKey()); iv + doFinal(plain) }
    private fun open(blob: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(128, blob, 0, 12)); doFinal(blob, 12, blob.size - 12)
    }

    fun hasPin(ctx: Context) = prefs(ctx).contains("real")
    fun hasDuress(ctx: Context) = prefs(ctx).contains("duress")

    private fun store(ctx: Context, slot: String, pin: String, secret: ByteArray) {
        val salt = PinCrypto.salt()
        prefs(ctx).edit().putString(slot, b64(seal(PinCrypto.wrap(pin, salt, secret)))).putString("${slot}_salt", b64(salt)).apply()
    }

    private fun load(ctx: Context, slot: String, pin: String): ByteArray? {
        val p = prefs(ctx)
        val blob = p.getString(slot, null) ?: return null
        return runCatching { PinCrypto.unwrap(pin, b64(p.getString("${slot}_salt", "")!!), open(b64(blob))) }.getOrNull()
    }

    /** First-run: creates the real profile's passphrase under [pin]. */
    fun create(ctx: Context, pin: String): Unlocked {
        val pass = PinCrypto.random(32)
        store(ctx, "real", pin, pass)
        return Unlocked(pass, false)
    }

    /** Checks a PIN. After [MAX_ATTEMPTS] consecutive failures the caller should wipe. */
    fun unlock(ctx: Context, pin: String): Unlocked? {
        val r = load(ctx, "real", pin)?.let { Unlocked(it, false) } ?: load(ctx, "duress", pin)?.let { Unlocked(it, true) }
        prefs(ctx).edit().putInt("fails", if (r == null) failures(ctx) + 1 else 0).apply()
        return r
    }

    fun failures(ctx: Context) = prefs(ctx).getInt("fails", 0)

    fun changePin(ctx: Context, passphrase: ByteArray, newPin: String) = store(ctx, "real", newPin, passphrase)

    /** The duress PIN opens a different, empty database. Pass null to remove it. */
    fun setDuress(ctx: Context, pin: String?) {
        if (pin == null) prefs(ctx).edit().remove("duress").remove("duress_salt").apply() else store(ctx, "duress", pin, PinCrypto.random(32))
    }

    // Admin PIN: gates admin settings only, it does not protect data.
    fun hasAdmin(ctx: Context) = prefs(ctx).contains("admin")
    fun setAdmin(ctx: Context, pin: String) { val s = PinCrypto.salt(); prefs(ctx).edit().putString("admin", b64(PinCrypto.derive(pin, s))).putString("admin_salt", b64(s)).apply() }
    fun checkAdmin(ctx: Context, pin: String): Boolean {
        val p = prefs(ctx)
        return p.getString("admin", null)?.let { b64(it).contentEquals(PinCrypto.derive(pin, b64(p.getString("admin_salt", "")!!))) } ?: false
    }

    // Biometric unlock: the passphrase is also stored under a Keystore key that only works after a biometric check.
    fun hasBiometric(ctx: Context) = prefs(ctx).contains("bio")
    fun biometricEncryptCipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, deviceKey(BIO_ALIAS, true)) }
    fun storeBiometric(ctx: Context, authorised: Cipher, passphrase: ByteArray) =
        prefs(ctx).edit().putString("bio", b64(authorised.iv + authorised.doFinal(passphrase))).apply()
    fun biometricDecryptCipher(ctx: Context): Cipher? = runCatching {
        val blob = b64(prefs(ctx).getString("bio", null) ?: return null)
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, deviceKey(BIO_ALIAS, true), GCMParameterSpec(128, blob, 0, 12)) }
    }.getOrNull()
    fun openBiometric(ctx: Context, authorised: Cipher): Unlocked? = runCatching {
        val blob = b64(prefs(ctx).getString("bio", null)!!)
        Unlocked(authorised.doFinal(blob, 12, blob.size - 12), false)
    }.getOrNull()
    fun clearBiometric(ctx: Context) {
        prefs(ctx).edit().remove("bio").apply()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(BIO_ALIAS) }
    }

    /** Panic wipe: destroys the keys first (making the data unrecoverable at once), then deletes every file. */
    fun wipe(ctx: Context) {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.apply { deleteEntry(ALIAS); deleteEntry(BIO_ALIAS) } }
        fun rm(f: File?) { f?.listFiles()?.forEach { if (it.isDirectory) rm(it); it.delete() } }
        rm(ctx.filesDir); rm(ctx.cacheDir); rm(ctx.getDatabasePath("x").parentFile); rm(File(ctx.applicationInfo.dataDir, "shared_prefs"))
        rm(ctx.getExternalFilesDir(null))
    }
}

object SecureDb {
    /** Opens (or creates) a SQLCipher database in the app's private storage. */
    fun open(ctx: Context, name: String, passphrase: ByteArray): SQLiteDatabase {
        System.loadLibrary("sqlcipher")
        val file = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        return SQLiteDatabase.openOrCreateDatabase(file, passphrase, null, null, null)
    }
}
