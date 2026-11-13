package app.pquest.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Random database passphrase, wrapped by a non-exportable AES key held in the Android Keystore. */
object KeyVault {
    private const val ALIAS = "pquest_master"
    private const val PREF = "vault"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build()
            )
            generateKey()
        }
    }

    @Synchronized
    fun passphrase(ctx: Context): ByteArray {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val stored = prefs.getString("db", null)
        if (stored != null) {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            return c.doFinal(raw, 12, raw.size - 12)
        }
        val pass = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit().putString("db", Base64.encodeToString(c.iv + c.doFinal(pass), Base64.NO_WRAP)).apply()
        return pass
    }
}

object SecureDb {
    /** Opens (or creates) a SQLCipher database in the app's private storage. */
    fun open(ctx: Context, name: String): SQLiteDatabase {
        System.loadLibrary("sqlcipher")
        val file = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        return SQLiteDatabase.openOrCreateDatabase(file, KeyVault.passphrase(ctx), null, null, null)
    }
}
