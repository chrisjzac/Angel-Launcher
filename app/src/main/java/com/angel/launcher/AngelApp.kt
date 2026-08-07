package com.angel.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.angel.launcher.gate.rememberUnlock
import com.angel.launcher.home.HomePane
import com.angel.launcher.home.HomeViewModel
import com.angel.launcher.launcher.AppsViewModel
import com.angel.launcher.launcher.LauncherPane
import com.angel.launcher.money.MoneyPane
import com.angel.launcher.money.MoneyViewModel
import com.angel.launcher.ui.Palette
import com.angel.launcher.ui.SkyWallpaper
import com.angel.launcher.weather.WeatherViewModel
import kotlinx.coroutines.delay
import java.util.Date

/** Home Assistant ← Launcher → Money. The launcher sits at index 1. */
@Composable
fun AngelApp(resetSignal: Int) {
    val context = LocalContext.current
    val apps: AppsViewModel = viewModel()
    val weather: WeatherViewModel = viewModel()
    val home: HomeViewModel = viewModel()
    val money: MoneyViewModel = viewModel()

    val sky by weather.sky.collectAsState()
    val skyMode by weather.mode.collectAsState()
    val temperature by weather.temp.collectAsState()
    val allApps by apps.apps.collectAsState()
    val icons by apps.icons.collectAsState()
    val pinnedKeys by apps.pinnedKeys.collectAsState()

    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    var letter by remember { mutableStateOf<Char?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var moneyUnlocked by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Date()) }
    // A pane that opens a file picker or a settings screen stops this activity.
    // That is not the user walking away, so it must not re-lock behind them.
    var skipRelockOnce by remember { mutableStateOf(false) }

    val calm = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    val pinned = remember(allApps, pinnedKeys) {
        pinnedKeys.mapNotNull { key -> allApps.firstOrNull { it.key == key } }
    }

    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) weather.refresh(force = true) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }

    // Home pressed while already home: back to the launcher, filter cleared.
    LaunchedEffect(resetSignal) {
        if (resetSignal == 0) return@LaunchedEffect
        letter = null
        pagerState.animateScrollToPage(1)
    }

    val unlockMoney = rememberUnlock("Money") { moneyUnlocked = true }

    // Home Assistant is open; only the figures on Money are gated. Re-lock
    // whenever the page loses focus, not on a timer.
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 0) home.connect() else home.disconnect()
        if (pagerState.currentPage != 2) moneyUnlocked = false
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (skipRelockOnce) {
                    skipRelockOnce = false
                } else {
                    moneyUnlocked = false
                    home.disconnect()
                }
                // Coming back is the other moment location may have been
                // turned on; the receiver only catches it while we are alive.
                Lifecycle.Event.ON_RESUME -> weather.refresh()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.fillMaxSize().background(Palette.Ground)) {
            SkyWallpaper(sky)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) { page ->
                when (page) {
                    0 -> HomePane(home, sky.accent)

                    1 -> LauncherPane(
                        apps = allApps,
                        pinned = pinned,
                        icons = icons,
                        letter = letter,
                        sky = sky,
                        skyMode = skyMode,
                        temperature = temperature,
                        now = now,
                        calm = calm,
                        torchOn = torchOn,
                        pageIndex = pagerState.currentPage,
                        onLetter = { letter = it },
                        // The pill only ever reports the weather, so tapping it
                        // asks for a reading rather than offering a palette.
                        onSkyTap = {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_COARSE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                weather.refresh(force = true)
                            } else {
                                askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        onLaunch = apps::launch,
                        onPin = apps::togglePin,
                        onMove = apps::movePinned,
                        onAppInfo = apps::openAppInfo,
                        onUninstall = apps::uninstall,
                        onTorch = { torchOn = it },
                    )

                    else -> MoneyPane(
                        model = money,
                        accent = sky.accent,
                        locked = !moneyUnlocked,
                        onUnlock = unlockMoney,
                        onLeaveForResult = { skipRelockOnce = true },
                    )
                }
            }
        }
    }
}
