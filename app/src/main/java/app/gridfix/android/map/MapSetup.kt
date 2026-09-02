package app.gridfix.android.map

import android.content.Context
import app.gridfix.android.BuildConfig
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import java.io.File

/** One selectable online base layer. */
data class BaseLayer(
    val key: String,
    val label: String,
    val source: ITileSource,
    val attribution: String,
    /** Highest zoom worth bulk-downloading from this provider. */
    val maxDownloadZoom: Int,
    /**
     * Whether the provider's usage terms permit pre-downloading an area. Only
     * public-domain USGS does; the others allow the ordinary browse cache only.
     */
    val bulkDownload: Boolean = false,
)

object MapSetup {

    private var initialized = false

    /** One-time osmdroid configuration: cache location, size, and the required user agent. */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val base = File(context.filesDir, "osmdroid")
        base.mkdirs()
        val tiles = File(base, "tiles")
        tiles.mkdirs()
        Configuration.getInstance().apply {
            osmdroidBasePath = base
            osmdroidTileCache = tiles
            userAgentValue = "MGRS GPS/" + BuildConfig.VERSION_NAME + " (rafaelm2002@gmail.com)"
            tileFileSystemCacheMaxBytes = 600L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 500L * 1024 * 1024
            // Keep serving cached tiles long after their nominal expiry — offline-first.
            expirationExtendedDuration = 30L * 24 * 60 * 60 * 1000
        }
    }

    private val esriWorldImagery = object : OnlineTileSourceBase(
        "EsriWorldImagery",
        0,
        19,
        256,
        "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
        "Esri, Maxar, Earthstar Geographics",
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex)
    }

    private val usgsTopo = object : OnlineTileSourceBase(
        "USGSTopo",
        0,
        16,
        256,
        "",
        arrayOf("https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/"),
        "USGS The National Map",
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex)
    }

    private val esriHillshade = object : OnlineTileSourceBase(
        "EsriHillshade",
        0,
        16,
        256,
        "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Elevation/World_Hillshade/MapServer/tile/"),
        "Esri, USGS, NASA",
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex)
    }

    // ---- MapTiler (keyed provider; the key is injected at build time from a CI secret) ----

    private const val MT_ATTR = "© MapTiler © OpenStreetMap contributors"

    /** True on builds made with the MAPTILER_KEY secret present. */
    val hasMapTiler: Boolean get() = BuildConfig.MAPTILER_KEY.isNotBlank()

    private fun mapTilerStyle(name: String, styleId: String, maxZoom: Int) = object : OnlineTileSourceBase(
        name,
        0,
        maxZoom,
        256,
        ".png",
        arrayOf("https://api.maptiler.com/maps/" + styleId + "/256/"),
        MT_ATTR,
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + ".png?key=" + BuildConfig.MAPTILER_KEY
    }

    private val mapTilerStreets by lazy { mapTilerStyle("MapTilerStreets", "streets-v2", 20) }
    private val mapTilerTopo by lazy { mapTilerStyle("MapTilerTopo", "topo-v2", 20) }

    private val mapTilerSatellite = object : OnlineTileSourceBase(
        "MapTilerSatellite",
        0,
        20,
        256,
        ".jpg",
        arrayOf("https://api.maptiler.com/tiles/satellite-v2/"),
        "© MapTiler",
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + ".jpg?key=" + BuildConfig.MAPTILER_KEY
    }

    /**
     * Base layers. With a MapTiler key (every store build) the Streets, Topo and
     * Satellite layers come from MapTiler under its commercial terms; without one
     * (a developer build missing the secret) the app falls back to the public
     * community servers so the map still works.
     */
    val baseLayers: List<BaseLayer> = if (hasMapTiler) listOf(
        BaseLayer(
            key = "streets",
            label = "Streets",
            source = mapTilerStreets,
            attribution = MT_ATTR,
            maxDownloadZoom = 16,
        ),
        BaseLayer(
            key = "topo",
            label = "Topographic",
            source = mapTilerTopo,
            attribution = MT_ATTR,
            maxDownloadZoom = 15,
        ),
        BaseLayer(
            key = "sat",
            label = "Satellite",
            source = mapTilerSatellite,
            attribution = "© MapTiler",
            maxDownloadZoom = 17,
        ),
        BaseLayer(
            key = "usgs",
            label = "USGS Topo (US only)",
            source = usgsTopo,
            attribution = "USGS The National Map",
            maxDownloadZoom = 15,
            bulkDownload = true,
        ),
        BaseLayer(
            key = "hillshade",
            label = "Hillshade",
            source = esriHillshade,
            attribution = "Esri, USGS, NASA",
            maxDownloadZoom = 14,
        ),
    ) else listOf(
        BaseLayer(
            key = "streets",
            label = "Streets",
            source = TileSourceFactory.MAPNIK,
            attribution = "© OpenStreetMap contributors",
            maxDownloadZoom = 16,
        ),
        BaseLayer(
            key = "topo",
            label = "Topographic",
            source = TileSourceFactory.OpenTopo,
            attribution = "© OpenStreetMap contributors, SRTM — style © OpenTopoMap (CC-BY-SA)",
            maxDownloadZoom = 15,
        ),
        BaseLayer(
            key = "sat",
            label = "Satellite",
            source = esriWorldImagery,
            attribution = "Esri, Maxar, Earthstar Geographics",
            maxDownloadZoom = 17,
        ),
        BaseLayer(
            key = "usgs",
            label = "USGS Topo (US only)",
            source = usgsTopo,
            attribution = "USGS The National Map",
            maxDownloadZoom = 15,
            bulkDownload = true,
        ),
        BaseLayer(
            key = "hillshade",
            label = "Hillshade",
            source = esriHillshade,
            attribution = "Esri, USGS, NASA",
            maxDownloadZoom = 14,
        ),
    )

    fun layerFor(key: String): BaseLayer = baseLayers.firstOrNull { it.key == key } ?: baseLayers[1]

    /** Folder where imported MBTiles files live. */
    fun mbtilesDir(context: Context): File =
        File(context.filesDir, "mbtiles").apply { mkdirs() }

    /** The hillshade tile source, reused by the hybrid shadow overlay. */
    val hillshadeSource: ITileSource get() = esriHillshade

    /**
     * Hybrid-terrain blend: hillshade tiles become shadow-only — white turns
     * transparent, dark slopes darken whatever base layer sits beneath.
     */
    val hillshadeShadowFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                -0.235f, -0.235f, -0.235f, 0f, 180f,
            )
        )
    )

    /**
     * Night-vision tile filter: collapses the basemap to shades of red on black,
     * matching the app's red-on-black night theme.
     */
    val nightTileFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                0.42f, 0.34f, 0.14f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}
