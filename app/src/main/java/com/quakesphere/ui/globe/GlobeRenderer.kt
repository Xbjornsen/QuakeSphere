package com.quakesphere.ui.globe

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.quakesphere.domain.model.Earthquake
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GlobeRenderer : GLSurfaceView.Renderer {

    // Matrices
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val mvMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(16)

    // Globe rotation state
    private var rotationX = 0f
    private var rotationY = 0f
    private var zoom = 1.0f
    private val MIN_ZOOM = 0.5f
    private val MAX_ZOOM = 3.0f

    // Globe shader program
    private var globeProgram = 0
    private var globePositionHandle = 0
    private var globeNormalHandle = 0
    private var globeTexCoordHandle = 0
    private var globeMVPHandle = 0
    private var globeMVHandle = 0
    private var globeNormalMatHandle = 0

    // Marker shader program
    private var markerProgram = 0
    private var markerPositionHandle = 0
    private var markerUVHandle = 0
    private var markerMVPHandle = 0
    private var markerColorHandle = 0
    private var markerAlphaHandle = 0
    private var markerSizeHandle = 0

    // Star shader program
    private var starProgram = 0
    private var starPositionHandle = 0
    private var starMVPHandle = 0
    private var starBrightnessHandle = 0

    // Globe mesh buffers
    private var sphereVertexBuffer: FloatBuffer? = null
    private var sphereIndexBuffer: ShortBuffer? = null
    private var sphereIndexCount = 0

    // Star field buffers
    private var starVertexBuffer: FloatBuffer? = null
    private var starCount = 0

    // Marker data
    private var earthquakes: List<Earthquake> = emptyList()
    private var markerPositions: List<FloatArray> = emptyList()
    private var markerQuadBuffer: FloatBuffer? = null
    private var selectedIndex: Int = -1

    // Tap callback
    var onMarkerTapped: ((Int) -> Unit)? = null

    // Viewport
    private var viewportWidth = 1
    private var viewportHeight = 1

    // Pulse animation
    private var pulseTime = 0f

    // ---- GLSL Shaders ----

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
        varying vec2 vTexCoord;
        varying vec3 vNormal;
        varying vec3 vWorldPos;

        float rand(vec2 co) {
            return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
        }

        float noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            float a = rand(i);
            float b = rand(i + vec2(1.0, 0.0));
            float c = rand(i + vec2(0.0, 1.0));
            float d = rand(i + vec2(1.0, 1.0));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }

        float fbm(vec2 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 5; i++) {
                v += a * noise(p);
                p *= 2.1;
                a *= 0.5;
            }
            return v;
        }

        void main() {
            vec2 uv = vTexCoord;

            float land = fbm(uv * vec2(3.0, 6.0) + vec2(1.7, 0.9));
            float isLand = smoothstep(0.48, 0.52, land);

            float poleBlend = abs(uv.y - 0.5) * 2.0;
            float pole = smoothstep(0.75, 0.85, poleBlend);
            isLand = max(isLand, pole);

            float oceanDetail = fbm(uv * 8.0) * 0.5;
            vec3 oceanDeep = vec3(0.04, 0.15, 0.45);
            vec3 oceanShallow = vec3(0.08, 0.28, 0.65);
            vec3 oceanColor = mix(oceanDeep, oceanShallow, oceanDetail);

            float terrainDetail = fbm(uv * 12.0);
            vec3 grassColor = vec3(0.18, 0.38, 0.12);
            vec3 desertColor = vec3(0.32, 0.28, 0.18);
            vec3 landColor = mix(grassColor, desertColor, terrainDetail);
            float rockDetail = fbm(uv * 20.0) * 0.4;
            vec3 rockColor = vec3(0.55, 0.55, 0.50);
            landColor = mix(landColor, rockColor, rockDetail);

            vec3 iceColor = vec3(0.92, 0.95, 1.0);

            vec3 color = mix(oceanColor, landColor, isLand * (1.0 - pole));
            color = mix(color, iceColor, pole);

            vec3 lightDir = normalize(vec3(1.0, 0.3, 0.5));
            vec3 n = normalize(vNormal);
            float diffuse = max(dot(n, lightDir), 0.0);
            float ambient = 0.15;
            color *= (ambient + diffuse * 0.85);

            vec3 viewDir = normalize(-vWorldPos);
            vec3 reflectDir = reflect(-lightDir, n);
            float spec = pow(max(dot(reflectDir, viewDir), 0.0), 32.0);
            color += (1.0 - isLand) * spec * 0.4 * vec3(0.8, 0.9, 1.0);

            float rim = 1.0 - abs(dot(n, viewDir));
            rim = pow(rim, 2.5);
            color += rim * vec3(0.2, 0.5, 1.0) * 0.8;

            gl_FragColor = vec4(color, 1.0);
        }
    """.trimIndent()

    private val MARKER_VERTEX_SHADER = """
        uniform mat4 uMVPMatrix;
        uniform float uSize;
        attribute vec4 aPosition;
        attribute vec2 aUV;
        varying vec2 vUV;
        void main() {
            vUV = aUV;
            gl_Position = uMVPMatrix * aPosition;
        }
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

    // ---- Renderer interface ----

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.02f, 0.04f, 0.10f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        globeProgram = createProgram(GLOBE_VERTEX_SHADER, GLOBE_FRAGMENT_SHADER)
        markerProgram = createProgram(MARKER_VERTEX_SHADER, MARKER_FRAGMENT_SHADER)
        starProgram = createProgram(STAR_VERTEX_SHADER, STAR_FRAGMENT_SHADER)

        setupGlobe()
        setupStarField()
        setupMarkerQuad()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height

        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        pulseTime += 0.05f

        Matrix.setLookAtM(viewMatrix, 0,
            0f, 0f, 3.5f / zoom,
            0f, 0f, 0f,
            0f, 1f, 0f
        )

        // Draw star field
        drawStarField()

        // Draw globe
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)

        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0)

        // Normal matrix = inverse transpose of MV
        Matrix.invertM(normalMatrix, 0, mvMatrix, 0)
        transposeMatrix(normalMatrix)

        drawGlobe()
        drawMarkers()
    }

    // ---- Setup methods ----

    private fun setupGlobe() {
        val (vertices, indices) = generateSphere(32, 64)
        sphereIndexCount = indices.size

        sphereVertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices); position(0) }

        sphereIndexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply { put(indices); position(0) }
    }

    private fun generateSphere(stacks: Int, slices: Int): Pair<FloatArray, ShortArray> {
        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Short>()

        for (i in 0..stacks) {
            val lat = (PI * i / stacks - PI / 2).toFloat()
            val cosLat = cos(lat)
            val sinLat = sin(lat)

            for (j in 0..slices) {
                val lon = (2.0 * PI * j / slices).toFloat()
                val cosLon = cos(lon)
                val sinLon = sin(lon)

                val x = cosLat * cosLon
                val y = sinLat
                val z = cosLat * sinLon

                // Position
                vertices.add(x)
                vertices.add(y)
                vertices.add(z)
                // Normal (same as position for unit sphere)
                vertices.add(x)
                vertices.add(y)
                vertices.add(z)
                // UV
                vertices.add(j.toFloat() / slices)
                vertices.add(i.toFloat() / stacks)
            }
        }

        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val first = (i * (slices + 1) + j).toShort()
                val second = (first + slices + 1).toShort()

                indices.add(first)
                indices.add(second)
                indices.add((first + 1).toShort())

                indices.add(second)
                indices.add((second + 1).toShort())
                indices.add((first + 1).toShort())
            }
        }

        return Pair(vertices.toFloatArray(), indices.toShortArray())
    }

    private fun setupStarField() {
        starCount = 1000
        val stars = FloatArray(starCount * 4) // x, y, z, brightness
        val r = java.util.Random(42L)
        for (i in 0 until starCount) {
            // Random point on a large sphere
            val theta = (r.nextFloat() * 2.0 * PI).toFloat()
            val phi = (acos(1.0 - 2.0 * r.nextFloat())).toFloat()
            val radius = 50f
            stars[i * 4] = radius * sin(phi) * cos(theta)
            stars[i * 4 + 1] = radius * sin(phi) * sin(theta)
            stars[i * 4 + 2] = radius * cos(phi)
            stars[i * 4 + 3] = 0.3f + r.nextFloat() * 0.7f
        }
        starVertexBuffer = ByteBuffer.allocateDirect(stars.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(stars); position(0) }
    }

    private fun setupMarkerQuad() {
        // Billboard quad: two triangles, centered at origin
        // Each vertex: x, y, z (world offset), u, v
        val quad = floatArrayOf(
            -1f, -1f, 0f, -1f, -1f,
             1f, -1f, 0f,  1f, -1f,
            -1f,  1f, 0f, -1f,  1f,
             1f,  1f, 0f,  1f,  1f
        )
        markerQuadBuffer = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(quad); position(0) }
    }

    // ---- Draw methods ----

    private fun drawStarField() {
        GLES20.glUseProgram(starProgram)

        val starMVP = FloatArray(16)
        // Stars don't rotate with the globe
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(starMVP, 0, projectionMatrix, 0, mvMatrix, 0)

        starMVPHandle = GLES20.glGetUniformLocation(starProgram, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(starMVPHandle, 1, false, starMVP, 0)

        starPositionHandle = GLES20.glGetAttribLocation(starProgram, "aPosition")
        starBrightnessHandle = GLES20.glGetAttribLocation(starProgram, "aBrightness")

        val STRIDE = 4 * 4 // 4 floats * 4 bytes
        starVertexBuffer?.position(0)
        GLES20.glVertexAttribPointer(starPositionHandle, 3, GLES20.GL_FLOAT, false, STRIDE, starVertexBuffer)
        GLES20.glEnableVertexAttribArray(starPositionHandle)

        starVertexBuffer?.position(3)
        GLES20.glVertexAttribPointer(starBrightnessHandle, 1, GLES20.GL_FLOAT, false, STRIDE, starVertexBuffer)
        GLES20.glEnableVertexAttribArray(starBrightnessHandle)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, starCount)

        GLES20.glDisableVertexAttribArray(starPositionHandle)
        GLES20.glDisableVertexAttribArray(starBrightnessHandle)
    }

    private fun drawGlobe() {
        GLES20.glUseProgram(globeProgram)

        globeMVPHandle = GLES20.glGetUniformLocation(globeProgram, "uMVPMatrix")
        globeMVHandle = GLES20.glGetUniformLocation(globeProgram, "uMVMatrix")
        globeNormalMatHandle = GLES20.glGetUniformLocation(globeProgram, "uNormalMatrix")
        globePositionHandle = GLES20.glGetAttribLocation(globeProgram, "aPosition")
        globeNormalHandle = GLES20.glGetAttribLocation(globeProgram, "aNormal")
        globeTexCoordHandle = GLES20.glGetAttribLocation(globeProgram, "aTexCoord")

        GLES20.glUniformMatrix4fv(globeMVPHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(globeMVHandle, 1, false, mvMatrix, 0)
        GLES20.glUniformMatrix4fv(globeNormalMatHandle, 1, false, normalMatrix, 0)

        val STRIDE = 8 * 4 // 8 floats per vertex * 4 bytes
        sphereVertexBuffer?.position(0)
        GLES20.glVertexAttribPointer(globePositionHandle, 3, GLES20.GL_FLOAT, false, STRIDE, sphereVertexBuffer)
        GLES20.glEnableVertexAttribArray(globePositionHandle)

        sphereVertexBuffer?.position(3)
        GLES20.glVertexAttribPointer(globeNormalHandle, 3, GLES20.GL_FLOAT, false, STRIDE, sphereVertexBuffer)
        GLES20.glEnableVertexAttribArray(globeNormalHandle)

        sphereVertexBuffer?.position(6)
        GLES20.glVertexAttribPointer(globeTexCoordHandle, 2, GLES20.GL_FLOAT, false, STRIDE, sphereVertexBuffer)
        GLES20.glEnableVertexAttribArray(globeTexCoordHandle)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIndexCount, GLES20.GL_UNSIGNED_SHORT, sphereIndexBuffer)

        GLES20.glDisableVertexAttribArray(globePositionHandle)
        GLES20.glDisableVertexAttribArray(globeNormalHandle)
        GLES20.glDisableVertexAttribArray(globeTexCoordHandle)
    }

    private fun drawMarkers() {
        if (markerPositions.isEmpty()) return

        GLES20.glUseProgram(markerProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)

        val markerMVP = GLES20.glGetUniformLocation(markerProgram, "uMVPMatrix")
        val markerColor = GLES20.glGetUniformLocation(markerProgram, "uColor")
        val markerAlpha = GLES20.glGetUniformLocation(markerProgram, "uAlpha")
        val markerSize = GLES20.glGetUniformLocation(markerProgram, "uSize")
        val posHandle = GLES20.glGetAttribLocation(markerProgram, "aPosition")
        val uvHandle = GLES20.glGetAttribLocation(markerProgram, "aUV")

        earthquakes.forEachIndexed { index, quake ->
            if (index >= markerPositions.size) return@forEachIndexed

            val pos = markerPositions[index]
            val size = (0.02f + (quake.mag - 4.0).toFloat() * 0.008f).coerceIn(0.02f, 0.08f)
            val alpha = if (index == selectedIndex) {
                0.9f + 0.1f * sin(pulseTime * 4f)
            } else {
                0.75f
            }

            // Depth-based color
            val (r, g, b) = when {
                quake.depth < 70.0 -> Triple(1.0f, 0.27f, 0.27f) // red - shallow
                quake.depth < 300.0 -> Triple(1.0f, 0.53f, 0.0f) // orange - intermediate
                else -> Triple(0.27f, 0.53f, 1.0f) // blue - deep
            }

            // Build billboard MVP: position at marker point, scaled by size
            val markerModel = FloatArray(16)
            Matrix.setIdentityM(markerModel, 0)

            // Position the billboard at the marker world position
            val markerMV = FloatArray(16)
            Matrix.multiplyMM(markerMV, 0, viewMatrix, 0, modelMatrix, 0)

            // Extract camera right and up vectors from view matrix for billboarding
            val right = floatArrayOf(markerMV[0], markerMV[4], markerMV[8])
            val up = floatArrayOf(markerMV[1], markerMV[5], markerMV[9])

            // Build billboard vertices
            val s = size
            val bverts = floatArrayOf(
                pos[0] + (-right[0] - up[0]) * s, pos[1] + (-right[1] - up[1]) * s, pos[2] + (-right[2] - up[2]) * s, -1f, -1f,
                pos[0] + ( right[0] - up[0]) * s, pos[1] + ( right[1] - up[1]) * s, pos[2] + ( right[2] - up[2]) * s,  1f, -1f,
                pos[0] + (-right[0] + up[0]) * s, pos[1] + (-right[1] + up[1]) * s, pos[2] + (-right[2] + up[2]) * s, -1f,  1f,
                pos[0] + ( right[0] + up[0]) * s, pos[1] + ( right[1] + up[1]) * s, pos[2] + ( right[2] + up[2]) * s,  1f,  1f
            )

            val bb = ByteBuffer.allocateDirect(bverts.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(bverts); position(0) }

            GLES20.glUniformMatrix4fv(markerMVP, 1, false, mvpMatrix, 0)
            GLES20.glUniform3f(markerColor, r, g, b)
            GLES20.glUniform1f(markerAlpha, alpha)
            GLES20.glUniform1f(markerSize, size)

            val STRIDE = 5 * 4
            bb.position(0)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, STRIDE, bb)
            GLES20.glEnableVertexAttribArray(posHandle)

            bb.position(3)
            GLES20.glVertexAttribPointer(uvHandle, 2, GLES20.GL_FLOAT, false, STRIDE, bb)
            GLES20.glEnableVertexAttribArray(uvHandle)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES20.glDisableVertexAttribArray(GLES20.glGetAttribLocation(markerProgram, "aPosition"))
        GLES20.glDisableVertexAttribArray(GLES20.glGetAttribLocation(markerProgram, "aUV"))
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    // ---- Public API ----

    fun updateEarthquakes(list: List<Earthquake>) {
        earthquakes = list
        markerPositions = list.map { quake ->
            latLonToXYZ(quake.lat.toFloat(), quake.lon.toFloat(), 1.02f)
        }
    }

    fun setRotation(dx: Float, dy: Float) {
        rotationY += dx * 0.3f
        rotationX += dy * 0.3f
        rotationX = rotationX.coerceIn(-90f, 90f)
    }

    fun setZoom(factor: Float) {
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun handleTap(normalizedX: Float, normalizedY: Float): Int {
        // Ray-sphere intersection per marker
        val eyeX = 0f
        val eyeY = 0f
        val eyeZ = 3.5f / zoom

        // Unproject tap to world ray
        val invVP = FloatArray(16)
        val vp = FloatArray(16)
        Matrix.multiplyMM(vp, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.invertM(invVP, 0, vp, 0)

        val nearClip = floatArrayOf(normalizedX, normalizedY, -1f, 1f)
        val farClip = floatArrayOf(normalizedX, normalizedY, 1f, 1f)

        val nearWorld = FloatArray(4)
        val farWorld = FloatArray(4)
        Matrix.multiplyMV(nearWorld, 0, invVP, 0, nearClip, 0)
        Matrix.multiplyMV(farWorld, 0, invVP, 0, farClip, 0)

        if (nearWorld[3] != 0f) { nearWorld[0] /= nearWorld[3]; nearWorld[1] /= nearWorld[3]; nearWorld[2] /= nearWorld[3] }
        if (farWorld[3] != 0f) { farWorld[0] /= farWorld[3]; farWorld[1] /= farWorld[3]; farWorld[2] /= farWorld[3] }

        val rayOrigin = floatArrayOf(nearWorld[0], nearWorld[1], nearWorld[2])
        val rayDir = normalize3(floatArrayOf(
            farWorld[0] - nearWorld[0],
            farWorld[1] - nearWorld[1],
            farWorld[2] - nearWorld[2]
        ))

        // Transform ray to model space (inverse of model matrix)
        val invModel = FloatArray(16)
        Matrix.invertM(invModel, 0, modelMatrix, 0)
        val rayOriginM = transformPoint(invModel, rayOrigin)
        val rayDirM = transformDir(invModel, rayDir)

        var bestIndex = -1
        var bestDist = Float.MAX_VALUE
        val tapRadius = 0.06f

        markerPositions.forEachIndexed { index, pos ->
            val dist = rayPointDistance(rayOriginM, rayDirM, pos)
            if (dist < tapRadius && dist < bestDist) {
                bestDist = dist
                bestIndex = index
            }
        }

        selectedIndex = bestIndex
        if (bestIndex >= 0) {
            onMarkerTapped?.invoke(bestIndex)
        }
        return bestIndex
    }

    fun setSelectedIndex(index: Int) {
        selectedIndex = index
    }

    // ---- Helper methods ----

    private fun latLonToXYZ(lat: Float, lon: Float, radius: Float = 1.02f): FloatArray {
        val latRad = Math.toRadians(lat.toDouble()).toFloat()
        val lonRad = Math.toRadians(lon.toDouble()).toFloat()
        return floatArrayOf(
            radius * cos(latRad) * cos(lonRad),
            radius * sin(latRad),
            radius * cos(latRad) * sin(lonRad)
        )
    }

    private fun transposeMatrix(m: FloatArray) {
        val temp = m.copyOf()
        for (i in 0..3) {
            for (j in 0..3) {
                m[i * 4 + j] = temp[j * 4 + i]
            }
        }
    }

    private fun normalize3(v: FloatArray): FloatArray {
        val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return if (len > 0.0001f) floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
        else floatArrayOf(0f, 0f, 1f)
    }

    private fun transformPoint(m: FloatArray, p: FloatArray): FloatArray {
        return floatArrayOf(
            m[0]*p[0] + m[4]*p[1] + m[8]*p[2] + m[12],
            m[1]*p[0] + m[5]*p[1] + m[9]*p[2] + m[13],
            m[2]*p[0] + m[6]*p[1] + m[10]*p[2] + m[14]
        )
    }

    private fun transformDir(m: FloatArray, d: FloatArray): FloatArray {
        return floatArrayOf(
            m[0]*d[0] + m[4]*d[1] + m[8]*d[2],
            m[1]*d[0] + m[5]*d[1] + m[9]*d[2],
            m[2]*d[0] + m[6]*d[1] + m[10]*d[2]
        )
    }

    private fun rayPointDistance(origin: FloatArray, dir: FloatArray, point: FloatArray): Float {
        val w = floatArrayOf(point[0] - origin[0], point[1] - origin[1], point[2] - origin[2])
        val t = w[0]*dir[0] + w[1]*dir[1] + w[2]*dir[2]
        val proj = floatArrayOf(origin[0] + t*dir[0], origin[1] + t*dir[1], origin[2] + t*dir[2])
        val dx = point[0] - proj[0]; val dy = point[1] - proj[1]; val dz = point[2] - proj[2]
        return sqrt(dx*dx + dy*dy + dz*dz)
    }

    private fun createProgram(vertSrc: String, fragSrc: String): Int {
        val vert = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc)
        val frag = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vert)
        GLES20.glAttachShader(program, frag)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vert)
        GLES20.glDeleteShader(frag)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }
}
