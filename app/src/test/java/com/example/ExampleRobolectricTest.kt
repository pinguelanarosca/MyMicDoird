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
    assertEquals("DroidMic", appName)
  }

  @Test
  fun `preferences manager save and load config`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesManager(context)
    val customConfig = StreamConfig(
        port = 9099,
        sampleRate = 44100,
        channels = 2,
        streamMode = StreamMode.TCP_SERVER,
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
    assertEquals(BufferPreset.ULTRA_LOW, loaded.bufferPreset)
    assertTrue(loaded.enableNoiseSuppressor)
    assertTrue(loaded.enableEchoCanceler)
    assertEquals(2.5f, loaded.gainMultiplier, 0.01f)
    assertFalse(loaded.keepScreenOn)
  }

  @Test
  fun `preferences manager handles github repo sanitization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesManager(context)

    prefs.saveGitHubRepo("  https://github.com/alexandreoops/droidmic/  ")
    assertEquals("alexandreoops/droidmic", prefs.getGitHubRepo())

    prefs.saveGitHubRepo("custom-user/custom-app")
    assertEquals("custom-user/custom-app", prefs.getGitHubRepo())
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
  fun `viewmodel generates valid ubuntu pipewire and fallback commands`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MainViewModel(app)

    // Default port 8088, 48000 Hz, mono
    val adbCmd = viewModel.getAdbForwardCommand()
    assertTrue(adbCmd.contains("adb forward tcp:8088 tcp:8088"))

    val pipeWireCmd = viewModel.getPipeWireCommand()
    assertTrue(pipeWireCmd.contains("module-null-sink"))
    assertTrue(pipeWireCmd.contains("media.class=Audio/Source/Virtual"))
    assertTrue(pipeWireCmd.contains("sink_name=DroidMic"))
    assertTrue(pipeWireCmd.contains("pw-cat -p --rate=48000 --channels=1"))

    val pulseAudioCmd = viewModel.getPulseAudioCommand()
    assertTrue(pulseAudioCmd.contains("module-pipe-source"))
    assertTrue(pulseAudioCmd.contains("mkfifo"))

    val script = viewModel.getCompleteBashScript()
    assertTrue(script.contains("trap cleanup INT TERM EXIT"))
    assertTrue(script.contains("pw-cat"))
    assertTrue(script.contains("pacat"))
    assertTrue(script.contains("sleep 2"))
  }
}
