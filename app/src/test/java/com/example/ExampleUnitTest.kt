package com.example

import com.example.data.GitHubUpdateManager
import com.example.data.PreferencesManager
import com.example.model.StreamConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExampleUnitTest {

  @Test
  fun testSemVerParsingAndPreReleasePrecedence() {
    // Normal upgrades
    assertTrue(GitHubUpdateManager.isNewerVersion("v1.0.1", "1.0.0"))
    assertTrue(GitHubUpdateManager.isNewerVersion("v1.1.0", "1.0.5"))
    assertTrue(GitHubUpdateManager.isNewerVersion("v2.0.0", "1.99.99"))

    // Downgrade / equal versions
    assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.0", "1.0.1"))
    assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.0", "1.0.0"))
    assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0", "1.0.0"))

    // Pre-release precedence according to SemVer 2.0.0 Rule 11
    // Normal release has higher precedence than pre-release: 1.0.0 > 1.0.0-rc1
    assertTrue(GitHubUpdateManager.isNewerVersion("1.0.0", "1.0.0-rc1"))
    assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0-rc1", "1.0.0"))

    // Comparing pre-releases between each other
    assertTrue(GitHubUpdateManager.isNewerVersion("1.0.0-rc.2", "1.0.0-rc.1"))
    assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0-rc.1", "1.0.0-rc.2"))

    // SemVer 2.0.0 Rule 10: Build metadata MUST be ignored in version precedence
    assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0+20130313144700", "1.0.0"))
    assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0", "1.0.0+exp.sha.5114f85"))
    assertTrue(GitHubUpdateManager.isNewerVersion("1.0.1+build.1", "1.0.0+build.999"))
  }

  @Test
  fun testPortValidationAndSanitization() {
    // Privileged/invalid ports (< 1024)
    assertFalse(StreamConfig.isValidPort(0))
    assertFalse(StreamConfig.isValidPort(80))
    assertFalse(StreamConfig.isValidPort(443))
    assertFalse(StreamConfig.isValidPort(1023))

    // Valid ports (1024..65535)
    assertTrue(StreamConfig.isValidPort(1024))
    assertTrue(StreamConfig.isValidPort(8088))
    assertTrue(StreamConfig.isValidPort(65535))

    // Out of range (> 65535)
    assertFalse(StreamConfig.isValidPort(65536))
    assertFalse(StreamConfig.isValidPort(99999))

    // Sanitization fallbacks
    assertEquals(8088, StreamConfig.sanitizePort(80))
    assertEquals(8088, StreamConfig.sanitizePort(70000))
    assertEquals(9090, StreamConfig.sanitizePort(9090))
  }

  @Test
  fun testRepositoryNameSanitization() {
    assertEquals("pinguelanarosca/MyMicDoird", PreferencesManager.sanitizeRepoName("pinguelanarosca/MyMicDoird"))
    assertEquals("pinguelanarosca/MyMicDoird", PreferencesManager.sanitizeRepoName("  https://github.com/pinguelanarosca/MyMicDoird/  "))
    assertEquals("pinguelanarosca/MyMicDoird", PreferencesManager.sanitizeRepoName("http://github.com/pinguelanarosca/MyMicDoird.git"))
  }

  @Test
  fun testLinuxScriptFileExistsAndHasAdbSerialHandling() {
    val scriptFile = listOf(
      File("linux/mymicdoird.sh"),
      File("../linux/mymicdoird.sh")
    ).firstOrNull { it.exists() } ?: File("linux/mymicdoird.sh")
    assertTrue("O arquivo linux/mymicdoird.sh deve existir no repositório", scriptFile.exists())
    val content = scriptFile.readText()

    // Must handle serial explicitly for multi-device environments
    assertTrue("Script deve usar adb -s com serial", content.contains("adb -s \"\$SERIAL\" forward"))
    assertTrue("Script deve verificar ANDROID_SERIAL", content.contains("ANDROID_SERIAL"))

    // Must NOT contain indiscriminate unloads of third-party sinks
    assertFalse("Script não deve descarregar todos os null-sinks do sistema", content.contains("pactl unload-module module-null-sink 2>/dev/null"))
    assertFalse("Script não deve descarregar todos os pipe-sources do sistema", content.contains("pactl unload-module module-pipe-source 2>/dev/null"))

    // Must use specific module ID cleanup
    assertTrue("Script deve descarregar MODULE_ID específico", content.contains("pactl unload-module \"\$MODULE_ID\""))
  }
}
