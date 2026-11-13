package com.droiddeck.launcher.storage

import android.os.Looper
import com.droiddeck.launcher.session.SessionPrefs
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StorageScanTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun eachClientsLeftoversAreCheckedAgainstItsOwnGames() {
        SessionPrefs.setGameStorage(app, SessionPrefs.GAME_STORAGE_OFF, "")
        val arm = SessionPrefs.steamRoot(app, "arm64")
        val x64 = SessionPrefs.steamRoot(app, "x86_64")
        File(arm, "steamapps/compatdata/10").mkdirs()
        File(arm, "steamapps/appmanifest_10.acf").writeText("\"appid\" \"10\"\n\"StateFlags\" \"4\"\n\"installdir\" \"Example\"\n")
        val removed = File(x64, "steamapps/compatdata/10").apply { mkdirs() }
        File(removed, "user.reg").writeText("x")

        val report = StorageScan.scan(app)
        assertTrue(report.leftoversChecked)
        assertEquals(listOf(removed.path), report.leftovers.map { it.prefix?.path })
    }

    @Test fun prefixTempIsClearedWhileSavesAndLinkedFoldersStay() {
        SessionPrefs.setGameStorage(app, SessionPrefs.GAME_STORAGE_OFF, "")
        val arm = SessionPrefs.steamRoot(app, "arm64")
        File(arm, "steamapps").mkdirs()
        File(arm, "steamapps/appmanifest_20.acf").writeText("\"appid\" \"20\"\n\"StateFlags\" \"4\"\n\"installdir\" \"Example\"\n")
        val local = File(arm, "steamapps/compatdata/20/pfx/drive_c/users/steamuser/AppData/Local")
        val temp = File(local, "Temp")
        val dumps = File(local, "CrashDumps")
        File(temp, "setup/payload.bin").apply { parentFile!!.mkdirs() }.writeBytes(ByteArray(1 shl 20))
        File(dumps, "game.dmp").apply { parentFile!!.mkdirs() }.writeText("dump")
        val save = File(local, "Game/Saved/save.dat").apply { parentFile!!.mkdirs() }
        save.writeText("save")
        val outside = File(app.filesDir, "outside").apply { mkdirs() }
        val kept = File(outside, "keep.dat").apply { writeText("keep") }
        val windows = File(arm, "steamapps/compatdata/20/pfx/drive_c/windows").apply { mkdirs() }
        Files.createSymbolicLink(File(windows, "temp").toPath(), outside.toPath())

        val game = StorageScan.scan(app).games.single { it.appId == 20 }
        assertEquals(listOf(temp.path, dumps.path), game.tempDirs.map { it.path })

        var done = false
        StorageState.clean(app, setOf(CleanItem.PREFIX_TEMP)) { done = true }
        val deadline = System.currentTimeMillis() + 30_000
        while (!done && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue(done)
        assertTrue(temp.isDirectory && temp.list()!!.isEmpty())
        assertTrue(dumps.isDirectory && dumps.list()!!.isEmpty())
        assertEquals("save", save.readText())
        assertEquals("keep", kept.readText())
    }
}
