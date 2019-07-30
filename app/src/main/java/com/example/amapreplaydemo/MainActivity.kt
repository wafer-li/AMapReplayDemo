package com.example.amapreplaydemo

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CustomRenderer
import com.amap.api.maps.model.*
import com.amap.api.maps.utils.overlay.MovingPointOverlay
import com.example.amapreplaydemo.bean.PointWrapper
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.parse
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity() {
    private lateinit var movingPointOverlay: MovingPointOverlay
    private lateinit var bounds: LatLngBounds

    private var isRunning = false
    private val passedPoints = mutableListOf<LatLng>()
    private var passPolyline: Polyline? = null

    @ImplicitReflectionSerializer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapView.onCreate(savedInstanceState)
        mapView.map.uiSettings.isTiltGesturesEnabled = false
        mapView.map.uiSettings.isRotateGesturesEnabled = false
        GlobalScope.launch(Dispatchers.Main) {
            val points = loadPoints(Dispatchers.Main)
            buildLine(points)
            val marker = buildMarker()
            buildMovingPointOverlay(marker)
            setUpMovingPointOverlay(movingPointOverlay, points)

            mapView.map.setCustomRenderer(object : CustomRenderer {
                override fun OnMapReferencechanged() {
                }

                override fun onDrawFrame(gl: GL10?) {
                    if (isRunning) {
                        passedPoints.add(movingPointOverlay.position)
                        passPolyline?.remove()
                        passPolyline = mapView.map.addPolyline(
                            PolylineOptions()
                                .addAll(passedPoints)
                                .color(Color.BLUE)
                                .width(10F)
                        )
                        if (movingPointOverlay.position == points.last()) {
                            passedPoints.clear()
                        }
                    }
                }

                override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                }

                override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                }
            })

            movingPointOverlay.setMoveListener {
                if (it == 0.0) {
                    isRunning = false
                    passedPoints.clear()
                    runOnUiThread {
                        btn.setText(R.string.start_replay)
                    }
                }
            }

            btn.setOnClickListener {
                if (isRunning) {
                    stop()
                } else {
                    start()
                }

                btn.setText(
                    if (isRunning) {
                        R.string.pause_replay
                    } else {
                        R.string.resume_replay
                    }
                )
            }
        }
    }

    private fun start() {
        isRunning = true
        mapView.map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50))
        movingPointOverlay.startSmoothMove()
    }

    private fun stop() {
        isRunning = false
        movingPointOverlay.stopMove()
    }

    private fun buildMarker(): Marker {
        return mapView.map.addMarker(MarkerOptions())
    }

    private fun buildMovingPointOverlay(marker: BasePointOverlay) {
        movingPointOverlay = MovingPointOverlay(mapView.map, marker)
    }

    private fun setUpMovingPointOverlay(movingPointOverlay: MovingPointOverlay, points: List<LatLng>) {
        movingPointOverlay.setPoints(points)
        movingPointOverlay.setTotalDuration(30)
    }

    private fun buildLine(points: List<LatLng>) {
        mapView.map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color(Color.RED)
                .width(10F)
        )
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (isRunning) {
            movingPointOverlay.startSmoothMove()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    @ImplicitReflectionSerializer
    private suspend fun loadPoints(coroutineContext: CoroutineContext): List<LatLng> {
        return withContext(coroutineContext) {
            val json = assets.open("points.json").bufferedReader().readText()
            val points = Json.parse<PointWrapper>(json).coordinates.map {
                LatLng(it[0], it[1])
            }

            val leastBound = LatLng(
                points.minBy { it.latitude }?.latitude ?: 0.0,
                points.minBy { it.longitude }?.longitude ?: 0.0
            )

            val maxBound = LatLng(
                points.maxBy { it.latitude }?.latitude ?: 0.0,
                points.maxBy { it.longitude }?.longitude ?: 0.0
            )

            bounds = LatLngBounds(leastBound, maxBound)
            points
        }
    }
}