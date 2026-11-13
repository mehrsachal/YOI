package app.pquest.symbology

import android.graphics.Color
import app.pquest.coords.LatLon
import app.pquest.cot.Kind
import app.pquest.cot.Sidc
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Runs the real mil-sym renderer off-device, so every symbol in the picker is proven to draw. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SymbolsTest {
    @Before fun init() = Symbols.init(org.robolectric.RuntimeEnvironment.getApplication())

    private fun inked(b: android.graphics.Bitmap): Int {
        var n = 0
        for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) if (Color.alpha(b.getPixel(x, y)) > 40) n++
        return n
    }

    @Test fun pickerIsAboutFortySymbolsInFiveTabs() {
        assertEquals(5, Catalog.tabs.size)
        assertTrue(Catalog.tabs.values.sumOf { it.size } in 38..50)
    }

    @Test fun everyPickerMarkerRenders() {
        for ((tab, list) in Catalog.tabs) for (e in list.filter { it.kind == Kind.MARKER }) {
            val bmp = Symbols.icon(e.sidc, "", 64)
            assertNotNull("$tab ${e.name} ${e.sidc}", bmp)
            assertTrue("$tab ${e.name} is blank", inked(bmp!!) > 30)
        }
    }

    @Test fun affiliationChangesFrameAndNameAddsLabel() {
        val f = Symbols.icon(Sidc.make('3', "10", "121100", "15"), "", 64)!!
        val h = Symbols.icon(Sidc.make('6', "10", "121100", "15"), "", 64)!!
        assertFalse("friendly and hostile frames must differ", f.sameAs(h))
        val named = Symbols.icon(Sidc.make('3', "10", "121100", "15"), "2 PL", 64)!!
        assertTrue(named.width > f.width || named.height > f.height)
        assertNull(Symbols.icon("custom:1", "", 64)); assertNull(Symbols.icon("", "", 64))
        // Hostile frames are red, friendly blue (APP-6 affiliation colours).
        fun dominant(b: android.graphics.Bitmap): String {
            var r = 0L; var bl = 0L
            for (y in 0 until b.height) for (x in 0 until b.width) { val p = b.getPixel(x, y); if (Color.alpha(p) > 200) { r += Color.red(p); bl += Color.blue(p) } }
            return if (r > bl) "red" else "blue"
        }
        assertEquals("blue", dominant(f)); assertEquals("red", dominant(h))
    }

    @Test fun everyPickerGraphicRendersToGeoJson() {
        val pts = listOf(LatLon(20.00, 64.00), LatLon(20.02, 64.03), LatLon(20.00, 64.06), LatLon(19.98, 64.03))
        val dump = StringBuilder()
        for (list in Catalog.tabs.values) for (e in list.filter { it.kind != Kind.MARKER && it.sidc.isNotEmpty() }) {
            val json = Symbols.graphic("g1", e.sidc, "RED", pts.take(maxOf(e.points, if (e.kind == Kind.AREA) 4 else 3)), 63.9, 19.9, 64.2, 20.1, 50000.0)
            assertNotNull("${e.name} ${e.sidc}", json)
            val o = org.json.JSONObject(json!!)
            assertTrue(e.name, o.getJSONArray("features").length() > 0)
            dump.append("### ${e.name}\n").append(json.take(1500)).append("\n")
        }
        File("build/graphic-samples.txt").writeText(dump.toString())
    }

    @Test fun searchCoversTheFullSet() {
        val r = Symbols.search("bridge")
        assertTrue(r.isNotEmpty())
        assertTrue(Symbols.search("infantry", '6').any { Sidc.affiliation(it.sidc) == '6' && Sidc.entity(it.sidc) == "121100" })
        assertTrue(Symbols.search("phase line").any { it.kind == Kind.LINE })
        assertTrue(Symbols.search("x").isEmpty())
        for (e in Symbols.search("mortar").filter { it.kind == Kind.MARKER }) assertNotNull(e.name, Symbols.icon(e.sidc, "", 48))
    }

    @Test fun badgesAndLabels() {
        val b = Symbols.badge("PQT", "Picquet 4", 64)
        assertTrue(inked(b) > 100)
        assertTrue(inked(Symbols.label("PL RED", 28f, Color.BLACK)) > 20)
    }
}
