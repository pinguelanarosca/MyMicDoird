package com.example

import android.app.Application
import android.content.Context
import android.media.AudioFormat
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.BufferPreset
import com.example.model.StreamConfig
import com.example.model.StreamMode
import com.example.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MyMicDoird", appName)
  }

  @Test
  fun `preferences manager default repo is pinguelanarosca MyMicDoird`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesManager(context)
    assertEquals("pinguelanarosca/MyMicDoird", prefs.getGitHubRepo())
  }

  @Test
  fun `preferences manager save and load config with loopback security`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesManager(context)
    val customConfig = StreamConfig(
        port = 9099,
        sampleRate = 44100,
        channels = 2,
        streamMode = StreamMode.TCP_SERVER,
        bindToLoopbackOnly = true,
        bufferPreset = BufferPreset.ULTRA_LOW,
        enableNoiseSuppressor = true,
        enableEchoCanceler = true,
        gainMultiplier = 2.5f,
        keepScreenOn = false
    )
    prefs.saveConfig(customConfig)
    val loaded = prefs.loadConfig()
    assertEquals(9099, loaded.port)
    assertEquals(44100, loaded.sampleRate)
    assertEquals(2, loaded.channels)
    assertTrue(loaded.bindToLoopbackOnly)
    assertEquals(BufferPreset.ULTRA_LOW, loaded.bufferPreset)
    assertTrue(loaded.enableNoiseSuppressor)
    assertTrue(loaded.enableEchoCanceler)
    assertEquals(2.5f, loaded.gainMultiplier, 0.01f)
    assertFalse(loaded.keepScreenOn)
  }

  @Test
  fun `stream config calculations for mono and stereo`() {
    val monoConfig = StreamConfig(sampleRate = 48000, channels = 1)
    assertEquals(AudioFormat.CHANNEL_IN_MONO, monoConfig.channelConfig)
    assertEquals(2, monoConfig.bytesPerSample) // 16-bit PCM = 2 bytes

    val stereoConfig = StreamConfig(sampleRate = 48000, channels = 2)
    assertEquals(AudioFormat.CHANNEL_IN_STEREO, stereoConfig.channelConfig)
    assertEquals(4, stereoConfig.bytesPerSample) // 16-bit PCM * 2 = 4 bytes
  }

  @Test
  fun `viewmodel generates ubuntu scripts that do not indiscriminately unload external audio modules`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MainViewModel(app)

    val script = viewModel.getCompleteBashScript()

    // Must NOT contain indiscriminate unloads
    assertFalse(
        "Script must not blindly unload all null-sinks from the host system",
        script.contains("pactl unload-module module-null-sink 2>/dev/null")
    )
    assertFalse(
        "Script must not blindly unload all pipe-sources from the host system",
        script.contains("pactl unload-module module-pipe-source 2>/dev/null")
    )

    // Must use specific module ID cleanup
    assertTrue(
        "Script must unload specific MODULE_ID",
        script.contains("pactl unload-module \"\$MODULE_ID\"")
    )

    // Must target MyMicDoird device name
    assertTrue(
        "Script must declare MyMicDoird sink",
        script.contains("sink_name=\"\$DEVICE_NAME\"")
    )
    assertTrue(
        "Script must have DEVICE_NAME MyMicDoird",
        script.contains("DEVICE_NAME=\"MyMicDoird\"")
    )
  }
}
