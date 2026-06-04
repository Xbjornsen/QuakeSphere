package com.quakesphere.ui.globe

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.quakesphere.domain.model.Earthquake
import com.quakesphere.domain.model.EarthquakeSwarm
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GlobeRenderer(private val appContext: android.content.Context) : GLSurfaceView.Renderer {

    // ── Matrices ────────────────────────────────────────────────────────────
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix       = FloatArray(16)
    private val modelMatrix      = FloatArray(16)
    private val mvpMatrix        = FloatArray(16)
    private val mvMatrix         = FloatArray(16)
    private val normalMatrix     = FloatArray(16)

    // ── Globe rotation / zoom ────────────────────────────────────────────────
    var rotationX = 0f
    var rotationY = 0f
    var zoom      = 1.0f
        private set
    private val MIN_ZOOM = 0.5f
    private val MAX_ZOOM = 3.5f

    // ── GL programs ──────────────────────────────────────────────────────────
    private var globeProgram  = 0
    private var markerProgram = 0
    private var starProgram   = 0
    private var lineProgram   = 0
    private var fillProgram   = 0

    // ── Globe mesh ───────────────────────────────────────────────────────────
    private var sphereVertexBuffer: FloatBuffer? = null
    private var sphereIndexBuffer: ShortBuffer?  = null
    private var sphereIndexCount = 0

    // ── Star field ───────────────────────────────────────────────────────────
    private var starVertexBuffer: FloatBuffer? = null
    private var starCount = 0

    // ── Continent lines ──────────────────────────────────────────────────────
    private var continentLineBuffer: FloatBuffer? = null
    private var continentLineVertexCount = 0

    // ── Continent fills ──────────────────────────────────────────────────────
    private var continentFillBuffer: FloatBuffer? = null
    private var continentFillVertexCount = 0

    @Volatile var lastMvpMatrix: FloatArray = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    // ── Earthquake markers ───────────────────────────────────────────────────
    @Volatile private var earthquakes: List<Earthquake> = emptyList()
    private var markerPositions: List<FloatArray>       = emptyList()
    private var selectedIndex = -1

    // ── Swarm data ───────────────────────────────────────────────────────────
    @Volatile private var swarms: List<EarthquakeSwarm> = emptyList()
    @Volatile private var swarmEventIds: Set<String>    = emptySet()

    // ── Settings ─────────────────────────────────────────────────────────────
    @Volatile var showContinentLines     = true
    @Volatile var showStars              = true
    @Volatile var autoRotate             = false
    @Volatile var markerColorByMagnitude = false

    // ── Tap callback ─────────────────────────────────────────────────────────
    var onMarkerTapped: ((Int) -> Unit)? = null

    // ── Viewport ─────────────────────────────────────────────────────────────
    private var viewportWidth  = 1
    private var viewportHeight = 1

    // ── Animation ────────────────────────────────────────────────────────────
    private var pulseTime = 0f

    // ════════════════════════════════════════════════════════════════════════
    // GLSL Shaders
    // ════════════════════════════════════════════════════════════════════════

    private val GLOBE_VERTEX_SHADER = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uMVMatrix;
        uniform mat4 uNormalMatrix;
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        varying vec3 vNormal;
        varying vec3 vWorldPos;
        void main() {
            vec4 worldPos = uMVMatrix * aPosition;
            vWorldPos = worldPos.xyz;
            vNormal = normalize(mat3(uNormalMatrix) * aNormal);
            vTexCoord = aTexCoord;
            gl_Position = uMVPMatrix * aPosition;
        }
    """.trimIndent()

    private val GLOBE_FRAGMENT_SHADER = """
        precision mediump float;
        uniform vec3 uSunDir;
        varying vec2 vTexCoord;
        varying vec3 vNormal;
        varying vec3 vWorldPos;

        void main() {
            // Pure deep-ocean base – continent fills rendered as separate geometry
            vec3 color = vec3(0.008, 0.030, 0.165);

            vec3 lightDir = normalize(uSunDir);
            vec3 n        = normalize(vNormal);
            float sunDot  = dot(n, lightDir);
            float diffuse = max(sunDot, 0.0);

            // Ocean diffuse
            color *= (0.14 + diffuse * 0.86);

            // Subtle specular shimmer (reduced from before)
            vec3 viewDir    = normalize(-vWorldPos);
            vec3 reflectDir = reflect(-lightDir, n);
            float spec = pow(max(dot(reflectDir, viewDir), 0.0), 55.0);
            color += spec * 0.14 * vec3(0.5, 0.75, 1.0);

            // Atmosphere rim glow
            float rim = 1.0 - abs(dot(n, viewDir));
            rim = pow(rim, 2.8);
            color += rim * vec3(0.10, 0.35, 1.0) * 0.65;

            // Night side
            float nightFactor = smoothstep(0.06, -0.16, sunDot);
            color = mix(color, color * 0.04, nightFactor);

            gl_FragColor = vec4(color, 1.0);
        }
    """.trimIndent()

    private val MARKER_VERTEX_SHADER = """
        uniform mat4 uMVPMatrix;
        uniform float uSize;
        attribute vec4 aPosition;
        attribute vec2 aUV;
        varying vec2 vUV;
        void main() { vUV = aUV; gl_Position = uMVPMatrix * aPosition; }
    """.trimIndent()

    private val MARKER_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec2 vUV;
        uniform vec3 uColor;
        uniform float uAlpha;
        void main() {
            float dist = length(vUV);
            if (dist > 1.0) discard;
            float core = smoothstep(0.3, 0.0, dist);
            float glow = smoothstep(1.0, 0.0, dist) * 0.6;
            float alpha = (core + glow) * uAlpha;
            vec3 color = mix(uColor * 1.5, uColor, dist);
            gl_FragColor = vec4(color, alpha);
        }
    """.trimIndent()

    private val STAR_VERTEX_SHADER = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        attribute float aBrightness;
        varying float vBrightness;
        void main() {
            vBrightness = aBrightness;
            gl_Position = uMVPMatrix * aPosition;
            gl_PointSize = 2.0;
        }
    """.trimIndent()

    private val STAR_FRAGMENT_SHADER = """
        precision mediump float;
        varying float vBrightness;
        void main() {
            gl_FragColor = vec4(vBrightness, vBrightness, vBrightness + 0.1, 1.0);
        }
    """.trimIndent()

    // Lit fill shader – used for continent polygon fills
    private val FILL_VERTEX_SHADER = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        varying vec3 vModelPos;
        void main() {
            vModelPos = aPosition.xyz;
            gl_Position = uMVPMatrix * aPosition;
        }
    """.trimIndent()

    private val FILL_FRAGMENT_SHADER = """
        precision mediump float;
        uniform vec3  uSunDirModel;
        uniform vec4  uFillColor;
        varying vec3  vModelPos;
        void main() {
            // Model-space normal == normalised position on unit sphere
            vec3 n = normalize(vModelPos);
            float sunDot  = dot(n, normalize(uSunDirModel));
            float diffuse = max(sunDot, 0.0);
            float night   = smoothstep(0.06, -0.16, sunDot);
            vec3 color = uFillColor.rgb * (0.15 + diffuse * 0.85);
            color = mix(color, color * 0.04, night);
            gl_FragColor = vec4(color, uFillColor.a);
        }
    """.trimIndent()

    // Simple colour line shader (continent outlines + swarm spines)
    private val LINE_VERTEX_SHADER = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        void main() { gl_Position = uMVPMatrix * aPosition; }
    """.trimIndent()

    private val LINE_FRAGMENT_SHADER = """
        precision mediump float;
        uniform vec4 uLineColor;
        void main() { gl_FragColor = uLineColor; }
    """.trimIndent()

    // ════════════════════════════════════════════════════════════════════════
    // GLSurfaceView.Renderer
    // ════════════════════════════════════════════════════════════════════════

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.02f, 0.04f, 0.10f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        globeProgram  = createProgram(GLOBE_VERTEX_SHADER,  GLOBE_FRAGMENT_SHADER)
        markerProgram = createProgram(MARKER_VERTEX_SHADER, MARKER_FRAGMENT_SHADER)
        starProgram   = createProgram(STAR_VERTEX_SHADER,   STAR_FRAGMENT_SHADER)
        lineProgram   = createProgram(LINE_VERTEX_SHADER,   LINE_FRAGMENT_SHADER)
        fillProgram   = createProgram(FILL_VERTEX_SHADER,   FILL_FRAGMENT_SHADER)

        setupGlobe()
        setupStarField()
        setupContinents()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth  = width
        viewportHeight = height
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        pulseTime += 0.05f
        if (autoRotate) {
            rotationY = (rotationY + 0.018f) % 360f   // gentle drift ~1 revolution per 5.5 min
        }

        Matrix.setLookAtM(viewMatrix, 0,
            0f, 0f, 4.8f / zoom,
            0f, 0f, 0f,
            0f, 1f, 0f
        )

        if (showStars) drawStarField()

        // Build globe MVP with current rotation
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0)
        Matrix.invertM(normalMatrix, 0, mvMatrix, 0)
        transposeMatrix(normalMatrix)

        lastMvpMatrix = mvpMatrix.copyOf()

        drawGlobe()
        drawContinentFills()                                    // filled land before outlines
        if (showContinentLines) drawContinentLines()
        drawPoleAxisLine()
        drawPoleIndicators()
        drawTsunamiRipples()
        drawSwarmSpines()
        drawMarkers()
    }

    // ════════════════════════════════════════════════════════════════════════
    // Setup
    // ════════════════════════════════════════════════════════════════════════

    private fun setupGlobe() {
        val (vertices, indices) = generateSphere(32, 64)
        sphereIndexCount = indices.size

        sphereVertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(vertices); position(0) }

        sphereIndexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
            .apply { put(indices); position(0) }
    }

    private fun generateSphere(stacks: Int, slices: Int): Pair<FloatArray, ShortArray> {
        val vertices = mutableListOf<Float>()
        val indices  = mutableListOf<Short>()
        for (i in 0..stacks) {
            val lat    = (PI * i / stacks - PI / 2).toFloat()
            val cosLat = cos(lat); val sinLat = sin(lat)
            for (j in 0..slices) {
                val lon    = (2.0 * PI * j / slices).toFloat()
                val cosLon = cos(lon); val sinLon = sin(lon)
                val x = cosLat * cosLon; val y = sinLat; val z = cosLat * sinLon
                vertices.addAll(listOf(x, y, z, x, y, z,
                    j.toFloat() / slices, i.toFloat() / stacks))
            }
        }
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val first  = (i * (slices + 1) + j).toShort()
                val second = (first + slices + 1).toShort()
                indices.addAll(listOf(first, second, (first + 1).toShort(),
                    second, (second + 1).toShort(), (first + 1).toShort()))
            }
        }
        return Pair(vertices.toFloatArray(), indices.toShortArray())
    }

    private fun setupStarField() {
        starCount = 1000
        val stars = FloatArray(starCount * 4)
        val r = java.util.Random(42L)
        for (i in 0 until starCount) {
            val theta  = (r.nextFloat() * 2.0 * PI).toFloat()
            val phi    = acos(1.0 - 2.0 * r.nextFloat()).toFloat()
            val radius = 50f
            stars[i*4]   = radius * sin(phi) * cos(theta)
            stars[i*4+1] = radius * sin(phi) * sin(theta)
            stars[i*4+2] = radius * cos(phi)
            stars[i*4+3] = 0.3f + r.nextFloat() * 0.7f
        }
        starVertexBuffer = ByteBuffer.allocateDirect(stars.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(stars); position(0) }
    }

    /**
     * Builds the continent fill mesh and outline mesh from Natural Earth GeoJSON.
     * Polygons are triangulated with [Earcut] (handles concave shapes correctly)
     * then projected onto the sphere.
     */
    private fun setupContinents() {
        val geometry = NaturalEarthLoader.load(appContext)

        continentFillVertexCount = geometry.fillVertices.size / 3
        continentFillBuffer = ByteBuffer.allocateDirect(geometry.fillVertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(geometry.fillVertices); position(0) }

        continentLineVertexCount = geometry.lineVertices.size / 3
        continentLineBuffer = ByteBuffer.allocateDirect(geometry.lineVertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(geometry.lineVertices); position(0) }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Draw methods
    // ════════════════════════════════════════════════════════════════════════

    private fun drawStarField() {
        GLES20.glUseProgram(starProgram)
        val starMVP = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)
        val tmpMV = FloatArray(16)
        Matrix.multiplyMM(tmpMV, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(starMVP, 0, projectionMatrix, 0, tmpMV, 0)

        val mvpH  = GLES20.glGetUniformLocation(starProgram, "uMVPMatrix")
        val posH  = GLES20.glGetAttribLocation(starProgram, "aPosition")
        val brigH = GLES20.glGetAttribLocation(starProgram, "aBrightness")
        GLES20.glUniformMatrix4fv(mvpH, 1, false, starMVP, 0)

        val STRIDE = 16
        starVertexBuffer?.position(0)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, STRIDE, starVertexBuffer)
        GLES20.glEnableVertexAttribArray(posH)
        starVertexBuffer?.position(3)
        GLES20.glVertexAttribPointer(brigH, 1, GLES20.GL_FLOAT, false, STRIDE, starVertexBuffer)
        GLES20.glEnableVertexAttribArray(brigH)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, starCount)
        GLES20.glDisableVertexAttribArray(posH)
        GLES20.glDisableVertexAttribArray(brigH)

        // Restore modelMatrix for globe
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)
    }

    private fun drawGlobe() {
        GLES20.glUseProgram(globeProgram)
        val mvpH    = GLES20.glGetUniformLocation(globeProgram, "uMVPMatrix")
        val mvH     = GLES20.glGetUniformLocation(globeProgram, "uMVMatrix")
        val normH   = GLES20.glGetUniformLocation(globeProgram, "uNormalMatrix")
        val sunH    = GLES20.glGetUniformLocation(globeProgram, "uSunDir")
        val posH    = GLES20.glGetAttribLocation(globeProgram, "aPosition")
        val normalH = GLES20.glGetAttribLocation(globeProgram, "aNormal")
        val texH    = GLES20.glGetAttribLocation(globeProgram, "aTexCoord")

        val sun = computeSunDirectionEyeSpace()
        GLES20.glUniformMatrix4fv(mvpH,  1, false, mvpMatrix,    0)
        GLES20.glUniformMatrix4fv(mvH,   1, false, mvMatrix,     0)
        GLES20.glUniformMatrix4fv(normH, 1, false, normalMatrix, 0)
        GLES20.glUniform3f(sunH, sun[0], sun[1], sun[2])

        val STRIDE = 32 // 8 floats × 4 bytes
        sphereVertexBuffer?.position(0)
        GLES20.glVertexAttribPointer(posH,    3, GLES20.GL_FLOAT, false, STRIDE, sphereVertexBuffer)
        GLES20.glEnableVertexAttribArray(posH)
        sphereVertexBuffer?.position(3)
        GLES20.glVertexAttribPointer(normalH, 3, GLES20.GL_FLOAT, false, STRIDE, sphereVertexBuffer)
        GLES20.glEnableVertexAttribArray(normalH)
        sphereVertexBuffer?.position(6)
        GLES20.glVertexAttribPointer(texH,    2, GLES20.GL_FLOAT, false, STRIDE, sphereVertexBuffer)
        GLES20.glEnableVertexAttribArray(texH)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIndexCount,
            GLES20.GL_UNSIGNED_SHORT, sphereIndexBuffer)

        GLES20.glDisableVertexAttribArray(posH)
        GLES20.glDisableVertexAttribArray(normalH)
        GLES20.glDisableVertexAttribArray(texH)
    }

    private fun drawContinentLines() {
        val buf = continentLineBuffer ?: return
        GLES20.glUseProgram(lineProgram)

        val posH   = GLES20.glGetAttribLocation(lineProgram,  "aPosition")
        val mvpH   = GLES20.glGetUniformLocation(lineProgram, "uMVPMatrix")
        val colorH = GLES20.glGetUniformLocation(lineProgram, "uLineColor")

        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0)
        // Soft blue-white continent lines
        GLES20.glUniform4f(colorH, 0.55f, 0.72f, 0.92f, 0.40f)

        buf.position(0)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 12, buf)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glLineWidth(1.5f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, continentLineVertexCount)
        GLES20.glDisableVertexAttribArray(posH)
    }

    private fun drawContinentFills() {
        val buf = continentFillBuffer ?: return
        GLES20.glUseProgram(fillProgram)

        val posH      = GLES20.glGetAttribLocation(fillProgram,  "aPosition")
        val mvpH      = GLES20.glGetUniformLocation(fillProgram, "uMVPMatrix")
        val sunH      = GLES20.glGetUniformLocation(fillProgram, "uSunDirModel")
        val colorH    = GLES20.glGetUniformLocation(fillProgram, "uFillColor")

        // Sun direction in model space = inv(modelMatrix) * sunDirWorld
        val sun       = computeSunDirectionEyeSpace()
        val invModel  = FloatArray(16)
        Matrix.invertM(invModel, 0, modelMatrix, 0)
        val sunModel  = transformDir(invModel, sun)

        GLES20.glUniformMatrix4fv(mvpH,   1, false, mvpMatrix, 0)
        GLES20.glUniform3f(sunH,   sunModel[0], sunModel[1], sunModel[2])
        GLES20.glUniform4f(colorH, 0.20f, 0.36f, 0.12f, 1.0f)

        buf.position(0)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 12, buf)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, continentFillVertexCount)
        GLES20.glDisableVertexAttribArray(posH)
    }

    private fun drawPoleAxisLine() {
        // Rotation axis: north pole (0,1.5,0) → south pole (0,-1.5,0) in model space
        val north = floatArrayOf(0f,  1.5f, 0f)
        val south = floatArrayOf(0f, -1.5f, 0f)
        drawLineSegment(north, south, 0.70f, 0.88f, 1.0f, 0.35f)
    }

    private fun drawMarkers() {
        if (markerPositions.isEmpty()) return
        GLES20.glUseProgram(markerProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)

        val mvpH    = GLES20.glGetUniformLocation(markerProgram, "uMVPMatrix")
        val colorH  = GLES20.glGetUniformLocation(markerProgram, "uColor")
        val alphaH  = GLES20.glGetUniformLocation(markerProgram, "uAlpha")
        val sizeH   = GLES20.glGetUniformLocation(markerProgram, "uSize")
        val posH    = GLES20.glGetAttribLocation(markerProgram,  "aPosition")
        val uvH     = GLES20.glGetAttribLocation(markerProgram,  "aUV")

        val currentEarthquakes = earthquakes
        val currentSwarmIds    = swarmEventIds

        currentEarthquakes.forEachIndexed { index, quake ->
            if (index >= markerPositions.size) return@forEachIndexed
            // Swarm events are rendered on spines instead
            if (currentSwarmIds.isNotEmpty() && quake.id in currentSwarmIds) return@forEachIndexed

            val pos   = markerPositions[index]
            val size  = (0.030f + (quake.mag - 4.0).toFloat() * 0.013f).coerceIn(0.030f, 0.13f)
            val alpha = if (index == selectedIndex)
                0.9f + 0.1f * sin(pulseTime * 4f) else 0.75f

            val (r, g, b) = quakeColor(quake)

            drawBillboardMarker(mvpH, colorH, alphaH, sizeH, posH, uvH, pos, size, r, g, b, alpha)
        }

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun drawSwarmSpines() {
        val currentSwarms = swarms
        if (currentSwarms.isEmpty()) return

        for (swarm in currentSwarms) {
            val base   = latLonToXYZ(swarm.centerLat.toFloat(), swarm.centerLon.toFloat(), 1.02f)
            val normal = normalize3(base)
            val spineLen = swarm.eventCount * 0.042f
            val tipR   = 1.02f + spineLen
            val tip    = floatArrayOf(normal[0]*tipR, normal[1]*tipR, normal[2]*tipR)

            // Spine line – warm gold
            drawLineSegment(base, tip, 1.0f, 0.75f, 0.1f, 0.85f)

            // Stacked dots along spine, largest at bottom (closest to surface)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            GLES20.glUseProgram(markerProgram)

            val mvpH   = GLES20.glGetUniformLocation(markerProgram, "uMVPMatrix")
            val colorH = GLES20.glGetUniformLocation(markerProgram, "uColor")
            val alphaH = GLES20.glGetUniformLocation(markerProgram, "uAlpha")
            val sizeH  = GLES20.glGetUniformLocation(markerProgram, "uSize")
            val posH   = GLES20.glGetAttribLocation(markerProgram,  "aPosition")
            val uvH    = GLES20.glGetAttribLocation(markerProgram,  "aUV")

            for ((i, event) in swarm.events.withIndex()) {
                val r    = 1.02f + i * 0.042f
                val pos  = floatArrayOf(normal[0]*r, normal[1]*r, normal[2]*r)
                val size = (0.036f + (event.mag - 4.0).toFloat() * 0.010f).coerceIn(0.032f, 0.080f)
                val (er, eg, eb) = quakeColor(event)
                val pulse = if (i == 0) 0.9f + 0.1f * sin(pulseTime * 3f) else 0.85f
                drawBillboardMarker(mvpH, colorH, alphaH, sizeH, posH, uvH, pos, size, er, eg, eb, pulse)
            }
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }
    }

    /** Draw a billboard marker at a pre-computed 3D world position. */
    private fun drawBillboardMarker(
        mvpH: Int, colorH: Int, alphaH: Int, sizeH: Int, posH: Int, uvH: Int,
        pos: FloatArray, size: Float, r: Float, g: Float, b: Float, alpha: Float
    ) {
        val right = floatArrayOf(mvMatrix[0], mvMatrix[4], mvMatrix[8])
        val up    = floatArrayOf(mvMatrix[1], mvMatrix[5], mvMatrix[9])
        val s = size

        val bverts = floatArrayOf(
            pos[0]+(-right[0]-up[0])*s, pos[1]+(-right[1]-up[1])*s, pos[2]+(-right[2]-up[2])*s, -1f,-1f,
            pos[0]+( right[0]-up[0])*s, pos[1]+( right[1]-up[1])*s, pos[2]+( right[2]-up[2])*s,  1f,-1f,
            pos[0]+(-right[0]+up[0])*s, pos[1]+(-right[1]+up[1])*s, pos[2]+(-right[2]+up[2])*s, -1f, 1f,
            pos[0]+( right[0]+up[0])*s, pos[1]+( right[1]+up[1])*s, pos[2]+( right[2]+up[2])*s,  1f, 1f
        )

        val bb = ByteBuffer.allocateDirect(bverts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(bverts); position(0) }

        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0)
        GLES20.glUniform3f(colorH, r, g, b)
        GLES20.glUniform1f(alphaH, alpha)
        GLES20.glUniform1f(sizeH, size)

        val STRIDE = 20
        bb.position(0)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, STRIDE, bb)
        GLES20.glEnableVertexAttribArray(posH)
        bb.position(3)
        GLES20.glVertexAttribPointer(uvH, 2, GLES20.GL_FLOAT, false, STRIDE, bb)
        GLES20.glEnableVertexAttribArray(uvH)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posH)
        GLES20.glDisableVertexAttribArray(uvH)
    }

    /** Draw a single GL_LINES segment using the line shader. */
    private fun drawLineSegment(p0: FloatArray, p1: FloatArray, r: Float, g: Float, b: Float, a: Float) {
        GLES20.glUseProgram(lineProgram)
        val verts = floatArrayOf(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2])
        val buf = ByteBuffer.allocateDirect(24)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(verts); position(0) }

        val posH   = GLES20.glGetAttribLocation(lineProgram,  "aPosition")
        val mvpH   = GLES20.glGetUniformLocation(lineProgram, "uMVPMatrix")
        val colorH = GLES20.glGetUniformLocation(lineProgram, "uLineColor")

        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(colorH, r, g, b, a)

        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 12, buf)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glLineWidth(2.5f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
        GLES20.glDisableVertexAttribArray(posH)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Pole indicators
    // ════════════════════════════════════════════════════════════════════════

    private fun drawPoleIndicators() {
        // North pole ring – cool white
        drawRingOnSphere(floatArrayOf(0f, 1.035f, 0f), 0.028f, 0.88f, 0.96f, 1.00f, 0.85f)
        // South pole ring – icy blue
        drawRingOnSphere(floatArrayOf(0f, -1.035f, 0f), 0.022f, 0.60f, 0.82f, 1.00f, 0.70f)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Tsunami ripples
    // ════════════════════════════════════════════════════════════════════════

    private fun drawTsunamiRipples() {
        val quakes = earthquakes
        val tsunamiQuakes = quakes.filter { it.tsunami == 1 }
        if (tsunamiQuakes.isEmpty()) return

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)  // additive glow

        val numRings   = 4
        val maxRadius  = 0.22f   // ~1400 km at globe scale

        for (quake in tsunamiQuakes) {
            val center = latLonToXYZ(quake.lat.toFloat(), quake.lon.toFloat(), 1.026f)
            val normal = normalize3(center)
            val (t1, t2) = tangentFrame(normal)

            for (ring in 0 until numRings) {
                val phase  = ((pulseTime * 0.30f) + ring.toFloat() / numRings) % 1.0f
                val radius = phase * maxRadius
                val alpha  = (1.0f - phase) * 0.75f
                if (alpha < 0.04f) continue
                drawRingOnSphere(center, radius, 0.20f, 0.72f, 1.00f, alpha,
                    t1Override = t1, t2Override = t2)
            }
        }

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    /**
     * Draw a GL_LINE_LOOP circle on the sphere surface.
     * [center] is the 3D world-space centre point (model space).
     * [radius] is the ring radius in GL units.
     * If t1Override/t2Override are null the tangent frame is auto-computed.
     */
    private fun drawRingOnSphere(
        center: FloatArray, radius: Float,
        r: Float, g: Float, b: Float, a: Float,
        t1Override: FloatArray? = null, t2Override: FloatArray? = null
    ) {
        val normal = normalize3(center)
        val (t1, t2) = if (t1Override != null && t2Override != null)
            Pair(t1Override, t2Override) else tangentFrame(normal)

        val segments = 40
        val verts = FloatArray(segments * 3)
        for (i in 0 until segments) {
            val angle = 2f * PI.toFloat() * i / segments
            verts[i*3]   = center[0] + t1[0]*cos(angle)*radius + t2[0]*sin(angle)*radius
            verts[i*3+1] = center[1] + t1[1]*cos(angle)*radius + t2[1]*sin(angle)*radius
            verts[i*3+2] = center[2] + t1[2]*cos(angle)*radius + t2[2]*sin(angle)*radius
        }
        val buf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(verts); position(0) }

        GLES20.glUseProgram(lineProgram)
        val posH   = GLES20.glGetAttribLocation(lineProgram,  "aPosition")
        val mvpH   = GLES20.glGetUniformLocation(lineProgram, "uMVPMatrix")
        val colorH = GLES20.glGetUniformLocation(lineProgram, "uLineColor")
        GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(colorH, r, g, b, a)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 12, buf)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glLineWidth(2.0f)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, segments)
        GLES20.glDisableVertexAttribArray(posH)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Public API
    // ════════════════════════════════════════════════════════════════════════

    fun updateEarthquakes(list: List<Earthquake>) {
        earthquakes     = list
        markerPositions = list.map { latLonToXYZ(it.lat.toFloat(), it.lon.toFloat(), 1.02f) }
    }

    fun updateSwarms(list: List<EarthquakeSwarm>) {
        swarms       = list
        swarmEventIds = list.flatMap { s -> s.events.map { it.id } }.toSet()
    }

    fun setRotation(dx: Float, dy: Float) {
        rotationY += dx * 0.3f
        rotationX  = (rotationX + dy * 0.3f).coerceIn(-90f, 90f)
    }

    fun setZoom(factor: Float) {
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun handleTap(normalizedX: Float, normalizedY: Float): Int {
        val vp = FloatArray(16)
        Matrix.multiplyMM(vp, 0, projectionMatrix, 0, viewMatrix, 0)
        val invVP = FloatArray(16)
        Matrix.invertM(invVP, 0, vp, 0)

        val nearClip = floatArrayOf(normalizedX, normalizedY, -1f, 1f)
        val farClip  = floatArrayOf(normalizedX, normalizedY,  1f, 1f)
        val nearW    = FloatArray(4); val farW = FloatArray(4)
        Matrix.multiplyMV(nearW, 0, invVP, 0, nearClip, 0)
        Matrix.multiplyMV(farW,  0, invVP, 0, farClip,  0)

        fun dehom(v: FloatArray) { if (v[3] != 0f) { v[0]/=v[3]; v[1]/=v[3]; v[2]/=v[3] } }
        dehom(nearW); dehom(farW)

        val rayDir = normalize3(floatArrayOf(farW[0]-nearW[0], farW[1]-nearW[1], farW[2]-nearW[2]))
        val invModel = FloatArray(16)
        Matrix.invertM(invModel, 0, modelMatrix, 0)
        val ro = transformPoint(invModel, floatArrayOf(nearW[0], nearW[1], nearW[2]))
        val rd = transformDir(invModel, rayDir)

        var bestIndex = -1; var bestDist = Float.MAX_VALUE
        markerPositions.forEachIndexed { i, pos ->
            val d = rayPointDistance(ro, rd, pos)
            if (d < 0.07f && d < bestDist) { bestDist = d; bestIndex = i }
        }

        selectedIndex = bestIndex
        if (bestIndex >= 0) onMarkerTapped?.invoke(bestIndex)
        return bestIndex
    }

    fun setSelectedIndex(index: Int) { selectedIndex = index }

    // ════════════════════════════════════════════════════════════════════════
    // Colour helpers
    // ════════════════════════════════════════════════════════════════════════

    private fun quakeColor(quake: Earthquake): Triple<Float, Float, Float> =
        if (markerColorByMagnitude) magnitudeColorFloat(quake.mag)
        else depthColorFloat(quake.depth)

    private fun magnitudeColorFloat(mag: Double) = when {
        mag < 5.0 -> Triple(0.30f, 0.69f, 0.31f)   // green
        mag < 6.0 -> Triple(1.00f, 0.92f, 0.23f)   // yellow
        mag < 7.0 -> Triple(1.00f, 0.60f, 0.00f)   // orange
        mag < 8.0 -> Triple(1.00f, 0.34f, 0.13f)   // deep-orange
        else       -> Triple(1.00f, 0.09f, 0.27f)   // red
    }

    private fun depthColorFloat(depth: Double) = when {
        depth < 70.0  -> Triple(1.0f, 0.27f, 0.27f)   // red  – shallow
        depth < 300.0 -> Triple(1.0f, 0.53f, 0.00f)   // orange – mid
        else           -> Triple(0.27f, 0.53f, 1.0f)  // blue – deep
    }

    // ════════════════════════════════════════════════════════════════════════
    // Math helpers
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Lat/lon → 3D position on a sphere, with axes:
     *   +Y = north pole, +Z = lon 90°E equator (camera-facing default),
     *   +X = lon 180° equator (screen-right when centred on lon 90°E).
     * The −cos(lon) on X makes increasing longitude move EASTWARD (rightward
     * when viewing from outside, with north up).
     */
    private fun latLonToXYZ(lat: Float, lon: Float, radius: Float = 1.02f): FloatArray {
        val latR = Math.toRadians(lat.toDouble()).toFloat()
        val lonR = Math.toRadians(lon.toDouble()).toFloat()
        return floatArrayOf(
            -radius * cos(latR) * cos(lonR),
            radius * sin(latR),
            radius * cos(latR) * sin(lonR)
        )
    }

    private fun transposeMatrix(m: FloatArray) {
        val t = m.copyOf()
        for (i in 0..3) for (j in 0..3) m[i*4+j] = t[j*4+i]
    }

    private fun normalize3(v: FloatArray): FloatArray {
        val len = sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2])
        return if (len > 0.0001f) floatArrayOf(v[0]/len, v[1]/len, v[2]/len)
        else floatArrayOf(0f, 0f, 1f)
    }

    private fun transformPoint(m: FloatArray, p: FloatArray) = floatArrayOf(
        m[0]*p[0]+m[4]*p[1]+m[8]*p[2]+m[12],
        m[1]*p[0]+m[5]*p[1]+m[9]*p[2]+m[13],
        m[2]*p[0]+m[6]*p[1]+m[10]*p[2]+m[14]
    )

    private fun transformDir(m: FloatArray, d: FloatArray) = floatArrayOf(
        m[0]*d[0]+m[4]*d[1]+m[8]*d[2],
        m[1]*d[0]+m[5]*d[1]+m[9]*d[2],
        m[2]*d[0]+m[6]*d[1]+m[10]*d[2]
    )

    private fun rayPointDistance(origin: FloatArray, dir: FloatArray, point: FloatArray): Float {
        val w = floatArrayOf(point[0]-origin[0], point[1]-origin[1], point[2]-origin[2])
        val t = w[0]*dir[0]+w[1]*dir[1]+w[2]*dir[2]
        val proj = floatArrayOf(origin[0]+t*dir[0], origin[1]+t*dir[1], origin[2]+t*dir[2])
        val dx=point[0]-proj[0]; val dy=point[1]-proj[1]; val dz=point[2]-proj[2]
        return sqrt(dx*dx+dy*dy+dz*dz)
    }

    /**
     * Real-time subsolar direction in eye/world space.
     * Camera has identity rotation so eye space == world space for directions.
     * Coordinate frame: Y=north, lon=0/lat=0 → (1,0,0), lon=90/lat=0 → (0,0,1).
     */
    private fun computeSunDirectionEyeSpace(): FloatArray {
        val cal  = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        val utcH = cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60.0
        val doy  = cal.get(java.util.Calendar.DAY_OF_YEAR)
        // Subsolar longitude: at UTC 12:00 the sun is directly over lon=0°
        val sunLon = Math.toRadians((12.0 - utcH) * 15.0)
        // Solar declination varies ±23.45° over the year
        val sunLat = Math.toRadians(-23.45 * cos(2.0 * Math.PI * (doy + 10) / 365.25))
        // Same axis convention as latLonToXYZ — negate X so the subsolar point
        // lines up with the day-side of the geometry.
        return floatArrayOf(
            (-cos(sunLat) * cos(sunLon)).toFloat(),
            sin(sunLat).toFloat(),
            (cos(sunLat) * sin(sunLon)).toFloat()
        )
    }

    /** Returns an orthonormal tangent frame (t1, t2) perpendicular to [normal]. */
    private fun tangentFrame(normal: FloatArray): Pair<FloatArray, FloatArray> {
        val ref = if (abs(normal[1]) < 0.9f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
        val t1  = normalize3(crossProduct(ref, normal))
        val t2  = normalize3(crossProduct(normal, t1))
        return Pair(t1, t2)
    }

    private fun crossProduct(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[1]*b[2] - a[2]*b[1],
        a[2]*b[0] - a[0]*b[2],
        a[0]*b[1] - a[1]*b[0]
    )

    // ════════════════════════════════════════════════════════════════════════
    // Shader compilation
    // ════════════════════════════════════════════════════════════════════════

    private fun createProgram(vertSrc: String, fragSrc: String): Int {
        val v = compileShader(GLES20.GL_VERTEX_SHADER,   vertSrc)
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, v); GLES20.glAttachShader(it, f)
            GLES20.glLinkProgram(it)
            GLES20.glDeleteShader(v);     GLES20.glDeleteShader(f)
        }
    }

    private fun compileShader(type: Int, source: String): Int =
        GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source)
            GLES20.glCompileShader(it)
        }
}
