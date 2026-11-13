package app.pquest.map

/** Builds the MapLibre style. Every tile URL points at the in-app gateway host, never at a real server. */
object StyleBuilder {
    const val HOST = "tiles.pquest.invalid"

    data class Raster(val id: String, val minZoom: Int, val maxZoom: Int, val opacity: Float = 1f, val bounds: BBox? = null)

    private fun tiles(id: String, v: Int) = "\"tiles\":[\"https://$HOST/$id/{z}/{x}/{y}?v=$v\"],\"tileSize\":256"

    fun build(base: Raster?, overlays: List<Raster>, demMaxZoom: Int?, hillshade: Boolean, night: Boolean, version: Int): String {
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
        for (g in listOf("track", "measure", "marks")) src += "\"$g\":{\"type\":\"geojson\",\"data\":{\"type\":\"FeatureCollection\",\"features\":[]}}"
        lay += "{\"id\":\"track\",\"type\":\"line\",\"source\":\"track\",\"layout\":{\"line-cap\":\"round\",\"line-join\":\"round\"},\"paint\":{\"line-color\":\"#d4007a\",\"line-width\":4}}"
        lay += "{\"id\":\"measure\",\"type\":\"line\",\"source\":\"measure\",\"layout\":{\"line-cap\":\"round\",\"line-join\":\"round\"},\"paint\":{\"line-color\":\"#0044ff\",\"line-width\":4}}"
        lay += "{\"id\":\"measure_pts\",\"type\":\"circle\",\"source\":\"measure\",\"paint\":{\"circle-radius\":7,\"circle-color\":\"#ffffff\",\"circle-stroke-color\":\"#0044ff\",\"circle-stroke-width\":3}}"
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
