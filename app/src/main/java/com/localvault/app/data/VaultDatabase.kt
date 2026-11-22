package com.localvault.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.localvault.app.security.CryptoManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [CredentialEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao

    companion object {
        private const val DB_NAME = "localvault_encrypted.db"

        @Volatile
        private var instance: VaultDatabase? = null

        @Volatile
        private var nativeLoaded: Boolean = false

        private fun ensureSqlCipherLoaded() {
            if (!nativeLoaded) {
                synchronized(this) {
                    if (!nativeLoaded) {
                        System.loadLibrary("sqlcipher")
                        nativeLoaded = true
                    }
                }
            }
        }

        fun getInstance(context: Context, dek: ByteArray): VaultDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext, dek).also {
                    instance = it
                }
            }
        }

        private fun buildDatabase(context: Context, dek: ByteArray): VaultDatabase {
            ensureSqlCipherLoaded()
            // DEK から DB パスフレーズ用コピーを生成（SupportOpenHelperFactory が使用後にゼロ化する）
            val passphraseBytes = dek.copyOf()
            val factory = SupportOpenHelperFactory(passphraseBytes)
            return Room.databaseBuilder(
                context,
                VaultDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .build()
        }

        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
