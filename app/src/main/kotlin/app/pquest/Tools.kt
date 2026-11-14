package app.pquest

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.pquest.coords.CoordText
import app.pquest.coords.Dtg
import app.pquest.coords.Geodesy
import app.pquest.coords.LatLon
import app.pquest.coords.North
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import org.maplibre.android.maps.MapLibreMap
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt

// ---- files and sharing ------------------------------------------------------------------------------------

fun displayName(ctx: Context, uri: Uri): String = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"

private fun shareDir(ctx: Context) = File(ctx.cacheDir, "share").apply { mkdirs() }

/** Hands a file to the Android share sheet (Bluetooth, Wi-Fi Direct, a file manager ...). */
fun shareFile(activity: Activity, name: String, mime: String, bytes: ByteArray) = runCatching {
    val f = shareDir(activity).resolve(name.replace(Regex("[^A-Za-z0-9._-]"), "_")); f.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(activity, "app.pquest.files", f)
    activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
}

fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

// ---- QR codes ---------------------------------------------------------------------------------------------

fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 2))
    val px = IntArray(size * size) { if (m.get(it % size, it / size)) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

fun decodeQr(src: Bitmap): String? {
    for (max in listOf(1400, 900, 2200)) runCatching {
        val k = minOf(1f, max / maxOf(src.width, src.height).toFloat())
        val b = if (k < 1f) Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true) else src
        val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(b.width, b.height, px))), mapOf(DecodeHintType.TRY_HARDER to true)).text
    }
    return null
}

@Composable
fun QrImage(text: String) {
    val bmp = remember(text) { runCatching { qrBitmap(text) }.getOrNull() } ?: return
    Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(1f))
}

/** Scans a QR code by taking a photo of it with the camera app (no camera permission, no live preview). */
@Composable
fun QrScanButton(activity: ComponentActivity, label: String, modifier: Modifier = Modifier, onText: (String?) -> Unit) {
    val file = remember { shareDir(activity).resolve("scan.jpg") }
    val uri = remember { FileProvider.getUriForFile(activity, "app.pquest.files", file) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val text = if (ok) runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 2 })?.let(::decodeQr) }.getOrNull() else null
        file.delete(); onText(text)
    }
    Big(label, modifier, filled = true) { runCatching { take.launch(uri) }.onFailure { onText(null) } }
}

// ---- map snapshot -----------------------------------------------------------------------------------------

/** Adds grid, scale bar, north arrow and date-time group to a picture of the map. */
fun decorate(app: App, map: MapLibreMap, shot: Bitmap, centre: LatLon, density: Float): Bitmap {
    val out = shot.copy(Bitmap.Config.ARGB_8888, true); val c = Canvas(out)
    val w = out.width.toFloat(); val h = out.height.toFloat()
    drawMgrsGrid(c, w, h, density, 1f, map, false)
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 3 * density; textSize = 14 * density; typeface = Typeface.DEFAULT_BOLD }
    val box = Paint().apply { color = Color.WHITE; alpha = 225 }
    // Scale bar: the longest round length that fits a third of the width.
    val p0 = map.projection.fromScreenLocation(android.graphics.PointF(0f, h / 2)); val p1 = map.projection.fromScreenLocation(android.graphics.PointF(w, h / 2))
    val mpp = Geodesy.distance(LatLon(p0.latitude, p0.longitude), LatLon(p1.latitude, p1.longitude)) / w
    val want = mpp * w / 3; val mag = 10.0.pow(kotlin.math.floor(kotlin.math.log10(want)))
    val len = listOf(5.0, 2.0, 1.0).map { it * mag }.first { it <= want }
    val bar = (len / mpp).toFloat(); val x = 12 * density; val y = h - 56 * density
    c.drawRect(x - 6 * density, y - 22 * density, x + bar + 6 * density, y + 10 * density, box)
    c.drawLine(x, y, x + bar, y, ink); c.drawLine(x, y - 6 * density, x, y + 6 * density, ink); c.drawLine(x + bar, y - 6 * density, x + bar, y + 6 * density, ink)
    c.drawText(if (len >= 1000) "${(len / 1000).roundToInt()} km" else "${len.roundToInt()} m", x, y - 8 * density, ink)
    // North arrow (the map is always north-up).
    val ax = w - 28 * density; val ay = 30 * density
    c.drawCircle(ax, ay + 6 * density, 22 * density, box)
    c.drawPath(android.graphics.Path().apply { moveTo(ax, ay - 12 * density); lineTo(ax + 9 * density, ay + 12 * density); lineTo(ax, ay + 6 * density); lineTo(ax - 9 * density, ay + 12 * density); close() }, ink)
    c.drawText("N", ax - 5 * density, ay + 26 * density, ink)
    val line = Dtg.format(System.currentTimeMillis(), app.settings.zulu) + "   " + CoordText.format(centre, app.settings.coordFormat)
    c.drawRect(0f, h - 30 * density, w, h, box); c.drawText(line, 12 * density, h - 10 * density, ink)
    return out
}

fun pdf(image: Bitmap, title: String): ByteArray {
    val doc = PdfDocument()
    val landscape = image.width > image.height
    val pw = if (landscape) 842 else 595; val ph = if (landscape) 595 else 842
    val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, 1).create())
    val k = minOf((pw - 40f) / image.width, (ph - 70f) / image.height)
    page.canvas.drawText(title, 20f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD })
    page.canvas.save(); page.canvas.translate(20f, 44f); page.canvas.scale(k, k)
    page.canvas.drawBitmap(image, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG)); page.canvas.restore()
    doc.finishPage(page)
    return ByteArrayOutputStream().also { doc.writeTo(it); doc.close() }.toByteArray()
}

// ---- compass ----------------------------------------------------------------------------------------------

@Composable
fun CompassSheet(app: App, ui: Ui) {
    val s = app.settings
    var magnetic by remember { mutableStateOf<Float?>(null) }
    var pressure by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm = app.getSystemService(SensorManager::class.java)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.sensor.type == Sensor.TYPE_PRESSURE) { pressure = e.values[0]; return }
                val r = FloatArray(9); SensorManager.getRotationMatrixFromVector(r, e.values)
                val o = FloatArray(3); SensorManager.getOrientation(r, o)
                magnetic = ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL) }
        onDispose { sm.unregisterListener(l) }
    }
    val at = ui.fix ?: ui.centre
    val decl = remember(at) { GeomagneticField(at.lat.toFloat(), at.lon.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble() }
    Title(stringResource(R.string.compass))
    Tip(app, "compass", R.string.tip_compass)
    val m = magnetic
    if (m == null) Text(stringResource(R.string.no_compass))
    else {
        val trueDeg = (m + decl + 360) % 360
        Text(bearingText(app, trueDeg, at), fontSize = 44.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(stringResource(R.string.back_azimuth, bearingText(app, North.back(trueDeg), at)), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Choice(listOf("GRID" to stringResource(R.string.north_grid), "MAG" to stringResource(R.string.north_mag), "TRUE" to stringResource(R.string.north_true)), s.north) { s.north = it }
    Choice(listOf(false to stringResource(R.string.degrees), true to stringResource(R.string.mils)), s.mils) { s.mils = it }
    Text(stringResource(R.string.declination_line, "%.1f".format(java.util.Locale.ROOT, decl), "%.1f".format(java.util.Locale.ROOT, North.convergence(at))))
    pressure?.let { Text(stringResource(R.string.baro_altitude, SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it).roundToInt()), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}
