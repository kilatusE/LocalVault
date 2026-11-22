package com.localvault.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.localvault.app.data.VaultManager
import com.localvault.app.security.VaultSessionManager

class LocalVaultApplication : Application() {
    lateinit var vaultManager: VaultManager
        private set

    override fun onCreate() {
        super.onCreate()
        vaultManager = VaultManager(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                val timeout = vaultManager.metaStore.autoLockSeconds.value
                VaultSessionManager.onAppBackgrounded(timeout)
            }

            override fun onStart(owner: LifecycleOwner) {
                val timeout = vaultManager.metaStore.autoLockSeconds.value
                VaultSessionManager.onAppForegrounded(timeout)
            }
        })
    }
}
