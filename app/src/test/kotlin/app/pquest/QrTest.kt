package app.pquest

import app.pquest.cot.Self
import app.pquest.missions.Links
import app.pquest.missions.Mission
import app.pquest.missions.Role
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** QR codes are drawn and read back with the same code the app uses on a device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QrTest {
    @Test fun missionQrSurvivesDrawingAndReading() {
        val link = Links.mission(Mission("m-1", "OP DEMO", "a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2U=", Role.OWNER), Role.VIEWER)
        assertEquals(link, decodeQr(qrBitmap(link)))
        assertEquals(Role.VIEWER, Links.parseMission(decodeQr(qrBitmap(link))!!)!!.role)
    }

    @Test fun demoMissionQrUsedInTheTourIsReadable() {
        val qr = Links.mission(demoMission())
        assertEquals("demo", Links.parseMission(decodeQr(qrBitmap(qr))!!)!!.id)
        val items = demoItems("x")
        assertEquals(4, items.size)
        // A small overlay also fits in one code and reads back.
        val overlay = Links.items(items, Self("u", "c"))!!
        assertEquals(items, Links.parseItems(decodeQr(qrBitmap(overlay, 1000))!!))
    }

    @Test fun blankPictureHasNoCode() = assertNull(decodeQr(android.graphics.Bitmap.createBitmap(300, 300, android.graphics.Bitmap.Config.ARGB_8888)))

    @Test fun quickReferenceCardHasEveryStepInBothLanguages() {
        val en = quickReferenceLines(RuntimeEnvironment.getApplication(), "en"); val ur = quickReferenceLines(RuntimeEnvironment.getApplication(), "ur")
        assertEquals(en.size, ur.size); assertEquals(10, en.count { it.second })
        assertTrue(en.any { it.first.contains("GO DARK") } && en.any { it.first.contains("41Q PC 1234 5678") })
        assertTrue("Urdu card must be in Urdu", ur.drop(1).all { line -> line.first.any { it in '\u0600'..'\u06FF' } })
        assertTrue(en.none { line -> line.first.any { it in '\u0600'..'\u06FF' } })
    }

    @Test fun everyTourStepHasATitleAndTasks() {
        assertEquals(11, TOUR_TITLES.size)
        assertEquals((2..9).toSet(), TOUR_TASKS.keys)
        assertTrue(TOUR_TASKS.values.all { it.isNotEmpty() })
    }
}
