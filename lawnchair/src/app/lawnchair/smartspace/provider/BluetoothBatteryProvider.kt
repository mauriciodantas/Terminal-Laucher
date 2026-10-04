package app.lawnchair.smartspace.provider

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.getSystemService
import app.lawnchair.smartspace.glance.BluetoothBattery
import app.lawnchair.smartspace.glance.BluetoothDeviceBattery
import app.lawnchair.smartspace.glance.GlanceKind
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.util.broadcastReceiverFlow
import com.android.launcher3.R
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shows the battery of the connected Bluetooth headphones, watch and the like. Without the
 * Bluetooth permission it shows nothing, and never blocks the other targets: the settings screen
 * explains what is missing.
 */
class BluetoothBatteryProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.glance_bluetooth,
        { glanceBluetooth },
    ) {
    private val adapter: BluetoothAdapter? = context.getSystemService<BluetoothManager>()?.adapter

    private val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        addAction(ACTION_BATTERY_LEVEL_CHANGED)
    }

    override val internalTargets: Flow<List<SmartspaceTarget>> = merge(
        broadcastReceiverFlow(context, filter).map { },
    )
        .onStart { emit(Unit) }
        .map { listOfNotNull(readTarget()) }
        .catch { emit(emptyList()) }

    private suspend fun readTarget(): SmartspaceTarget? {
        if (!hasPermission(context)) return null
        val devices = connectedDevices()
        val summary = BluetoothBattery.summarize(devices) ?: return null
        return SmartspaceTarget(
            id = GlanceKind.BLUETOOTH_ID,
            headerAction = SmartspaceAction(
                id = "${GlanceKind.BLUETOOTH_ID}Action",
                icon = Icon.createWithResource(context, R.drawable.ic_battery_low),
                title = summary.title,
                subtitle = summary.subtitle,
            ),
            score = SmartspaceScores.SCORE_BATTERY,
            featureType = SmartspaceTarget.FeatureType.FEATURE_TIPS,
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectedDevices(): List<BluetoothDeviceBattery> {
        val adapter = adapter?.takeIf { it.isEnabled } ?: return emptyList()
        val devices = linkedSetOf<BluetoothDevice>()
        for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            devices += proxyDevices(adapter, profile)
        }
        return devices.mapNotNull { device ->
            val percent = batteryLevel(device) ?: return@mapNotNull null
            BluetoothDeviceBattery(device.name ?: device.address, percent)
        }
    }

    /** Connected devices of one profile, or nothing when the profile does not answer in time. */
    @SuppressLint("MissingPermission")
    private suspend fun proxyDevices(adapter: BluetoothAdapter, profile: Int): List<BluetoothDevice> =
        withTimeoutOrNull(PROXY_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        val list = runCatching { proxy.connectedDevices }.getOrDefault(emptyList())
                        adapter.closeProfileProxy(profile, proxy)
                        if (continuation.isActive) continuation.resume(list)
                    }

                    override fun onServiceDisconnected(profile: Int) = Unit
                }
                val started = runCatching { adapter.getProfileProxy(context, listener, profile) }
                    .getOrDefault(false)
                if (!started && continuation.isActive) continuation.resume(emptyList())
            }
        } ?: emptyList()

    /** The battery the device reports, 0 to 100, or null when it does not report one. */
    private fun batteryLevel(device: BluetoothDevice): Int? = runCatching {
        // getBatteryLevel is not part of the public SDK, so it is read by reflection and a device
        // or Android version that does not answer is simply left out.
        val level = BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as Int
        level.takeIf { it in 0..100 }
    }.getOrNull()

    companion object {
        private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val PROXY_TIMEOUT_MS = 2_000L

        /** True when the app may read the connected devices (needs the runtime permission from Android 12). */
        fun hasPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }
}
