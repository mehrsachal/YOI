package app.pquest.map

/** Builds the MapLibre style. Every tile URL points at the in-app gateway host, never at a real server. */
object StyleBuilder {
    const val HOST = "tiles.pquest.invalid"

    data class Raster(val id: String, val minZoom: Int, val maxZoom: Int, val opacity: Float = 1f, val bounds: BBox? = null)

    private fun tiles(id: String, v: Int) = "\"tiles\":[\"https://$HOST/$id/{z}/{x}/{y}?v=$v\"],\"tileSize\":256"

    fun build(base: Raster?, overlays: List<Raster>, demMaxZoom: Int?, hillshade: Boolean, night: Boolean, version: Int, slope: Boolean = false, contours: Boolean = false): String {
        val src = ArrayList<String>()
        val lay = ArrayList<String>()
        val dim = if (night) ",\"raster-brightness-max\":0.45,\"raster-saturation\":-0.5" else ""
        lay += "{\"id\":\"bg\",\"type\":\"background\",\"paint\":{\"background-color\":\"${if (night) "#0a0000" else "#e9e6dc"}\"}}"
        for (r in listOfNotNull(base) + overlays) {
            val b = r.bounds?.let { ",\"bounds\":[${it.west},${it.south},${it.east},${it.north}]" } ?: ""
            src += "\"r_${r.id}\":{\"type\":\"raster\",${tiles(r.id, version)},\"minzoom\":${r.minZoom},\"maxzoom\":${r.maxZoom}$b}"
            lay += "{\"id\":\"r_${r.id}\",\"type\":\"raster\",\"source\":\"r_${r.id}\",\"paint\":{\"raster-opacity\":${r.opacity},\"raster-fade-duration\":0$dim}}"
        }
        if (demMaxZoom != null && hillshade) {
            src += "\"dem\":{\"type\":\"raster-dem\",${tiles("dem", version)},\"encoding\":\"terrarium\",\"maxzoom\":$demMaxZoom}"
            lay += "{\"id\":\"hillshade\",\"type\":\"hillshade\",\"source\":\"dem\",\"paint\":{\"hillshade-exaggeration\":0.45,\"hillshade-shadow-color\":\"#2b2118\"}}"
        }
        if (demMaxZoom != null) for ((id, on) in listOf("slope" to slope, "contour" to contours)) if (on) {
            src += "\"$id\":{\"type\":\"raster\",${tiles(id, version)},\"maxzoom\":$demMaxZoom}"
            lay += "{\"id\":\"$id\",\"type\":\"raster\",\"source\":\"$id\",\"paint\":{\"raster-fade-duration\":0,\"raster-resampling\":\"nearest\"}}"
        }
        for (g in listOf("track", "measure", "marks", "gfx", "labels", "items", "contacts")) src += "\"$g\":{\"type\":\"geojson\",\"data\":{\"type\":\"FeatureCollection\",\"features\":[]}}"
        lay += "{\"id\":\"track\",\"type\":\"line\",\"source\":\"track\",\"layout\":{\"line-cap\":\"round\",\"line-join\":\"round\"},\"paint\":{\"line-color\":\"#d4007a\",\"line-width\":4}}"
        lay += "{\"id\":\"measure\",\"type\":\"line\",\"source\":\"measure\",\"layout\":{\"line-cap\":\"round\",\"line-join\":\"round\"},\"paint\":{\"line-color\":\"#0044ff\",\"line-width\":4}}"
        lay += "{\"id\":\"measure_pts\",\"type\":\"circle\",\"source\":\"measure\",\"paint\":{\"circle-radius\":7,\"circle-color\":\"#ffffff\",\"circle-stroke-color\":\"#0044ff\",\"circle-stroke-width\":3}}"
        // Tactical graphics come from the symbol renderer as lines, fills and label points; point symbols are images.
        lay += "{\"id\":\"gfx_fill\",\"type\":\"fill\",\"source\":\"gfx\",\"filter\":[\"all\",[\"==\",[\"geometry-type\"],\"Polygon\"],[\"has\",\"fillColor\"]],\"paint\":{\"fill-color\":[\"get\",\"fillColor\"],\"fill-opacity\":[\"coalesce\",[\"get\",\"fillOpacity\"],0.15]}}"
        lay += "{\"id\":\"gfx_line\",\"type\":\"line\",\"source\":\"gfx\",\"filter\":[\"has\",\"strokeColor\"],\"layout\":{\"line-cap\":\"round\",\"line-join\":\"round\"},\"paint\":{\"line-color\":[\"get\",\"strokeColor\"],\"line-width\":[\"coalesce\",[\"get\",\"strokeWidth\"],3]}}"
        for (id in listOf("labels", "items", "contacts")) lay += "{\"id\":\"$id\",\"type\":\"symbol\",\"source\":\"$id\",\"layout\":{\"icon-image\":[\"get\",\"i\"],\"icon-allow-overlap\":true,\"icon-ignore-placement\":true,\"icon-rotate\":[\"coalesce\",[\"get\",\"angle\"],0],\"icon-rotation-alignment\":\"map\"}}"
        lay += "{\"id\":\"marks\",\"type\":\"circle\",\"source\":\"marks\",\"paint\":{\"circle-radius\":9,\"circle-color\":[\"get\",\"c\"],\"circle-stroke-color\":\"#ffffff\",\"circle-stroke-width\":3}}"
        return "{\"version\":8,\"sources\":{${src.joinToString(",")}},\"layers\":[${lay.joinToString(",")}]}"
    }

    fun line(pts: List<app.pquest.coords.LatLon>): String {
        val coords = pts.joinToString(",") { "[${it.lon},${it.lat}]" }
        val feats = ArrayList<String>()
        if (pts.size >= 2) feats += "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coords]}}"
        if (pts.isNotEmpty()) feats += "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"MultiPoint\",\"coordinates\":[$coords]}}"
        return "{\"type\":\"FeatureCollection\",\"features\":[${feats.joinToString(",")}]}"
    }

    fun marks(pts: List<Pair<app.pquest.coords.LatLon, String>>): String = "{\"type\":\"FeatureCollection\",\"features\":[" +
        pts.joinToString(",") { (p, c) -> "{\"type\":\"Feature\",\"properties\":{\"c\":\"$c\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[${p.lon},${p.lat}]}}" } + "]}"
}
