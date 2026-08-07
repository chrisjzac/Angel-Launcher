package com.angel.launcher.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.angel.launcher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
) {
    val key: String get() = "$packageName/$activityName"
    val initial: Char
        get() = label.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '#'
}

class AppsViewModel(app: Application) : AndroidViewModel(app) {

    private val _apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val apps: StateFlow<List<LaunchableApp>> = _apps.asStateFlow()

    private val _icons = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val icons: StateFlow<Map<String, ImageBitmap>> = _icons.asStateFlow()

    val pinnedKeys: StateFlow<List<String>> =
        Prefs.pinned(app).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val packageWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, packageWatcher, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(packageWatcher) }
    }

    fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val pm = context.packageManager
            val list = withContext(Dispatchers.IO) {
                val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(main, 0)
                    .asSequence()
                    .filter { it.activityInfo.packageName != context.packageName }
                    .map {
                        LaunchableApp(
                            label = it.loadLabel(pm).toString().trim(),
                            packageName = it.activityInfo.packageName,
                            activityName = it.activityInfo.name,
                        )
                    }
                    .distinctBy { it.key }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }
            _apps.value = list

            if (pinnedKeys.value.isEmpty()) seedPinned(list)

            val bitmaps = withContext(Dispatchers.IO) {
                list.associate { app ->
                    val icon = runCatching {
                        pm.getActivityIcon(ComponentName(app.packageName, app.activityName))
                    }.getOrNull() ?: runCatching {
                        pm.getApplicationIcon(app.packageName)
                    }.getOrNull()
                    app.key to icon?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap()
                }.filterValues { it != null }.mapValues { it.value!! }
            }
            _icons.value = bitmaps
        }
    }

    /** Phone · Messages · Camera · Maps · Music · Settings, resolved on this device. */
    private suspend fun seedPinned(all: List<LaunchableApp>) {
        val pm = getApplication<Application>().packageManager
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
            Intent(Settings.ACTION_SETTINGS),
        )
        val packages = probes.mapNotNull { intent ->
            runCatching {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull()
        }
        val seeded = packages.mapNotNull { pkg -> all.firstOrNull { it.packageName == pkg } }
            .map { it.key }
            .distinct()
        if (seeded.isNotEmpty()) Prefs.setPinned(getApplication(), seeded)
    }

    fun togglePin(app: LaunchableApp) {
        viewModelScope.launch {
            val current = pinnedKeys.value
            val next = if (app.key in current) current - app.key else current + app.key
            Prefs.setPinned(getApplication(), next)
        }
    }

    /** Drag-to-reorder on the pinned list. Positions are the stored order. */
    fun movePinned(from: Int, to: Int) {
        val current = pinnedKeys.value
        if (from !in current.indices || to !in current.indices || from == to) return
        val next = current.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { Prefs.setPinned(getApplication(), next) }
    }

    fun uninstall(app: LaunchableApp) {
        val intent = Intent(Intent.ACTION_DELETE)
            .setData(Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun launch(app: LaunchableApp) {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = context.packageManager.getLaunchIntentForPackage(app.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (fallback != null) runCatching { context.startActivity(fallback) }
        }
    }

    fun openAppInfo(app: LaunchableApp) {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        const val ICON_PX = 96
    }
}
