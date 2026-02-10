package com.example.musicsearch

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GestureDetectorCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.abs

class DetalleActivity : MusicBaseActivity() {

    private var cancionActual: Cancion? = null
    private var esReproduciendo = false

    // Animadores
    private var animadorGiroVinilo: ObjectAnimator? = null
    private var animadorTech: ObjectAnimator? = null

    // Detector de gestos
    private lateinit var gestureDetector: GestureDetectorCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle)

        // CONFIGURAR BOTÓN ATRÁS (YA NO DEBERÍA FALLAR)
        val btnAtras = findViewById<View>(R.id.btnVolver)
        btnAtras.setOnClickListener {
            finish()
        }

        // Recuperar ID y cargar datos
        val idCancion = intent.getIntExtra("ID_CANCION", -1)
        if (idCancion != -1) {
            val db = AdminSQL(this)
            cancionActual = db.obtenerCancionPorId(idCancion)
            if (cancionActual != null) {
                cargarDatos(cancionActual!!)
            }
        }

        // CONFIGURAR BOTÓN PLAY
        val fabPlay = findViewById<FloatingActionButton>(R.id.fabPlayDetalle)
        fabPlay.setOnClickListener { toggleReproduccion() }

        // CONFIGURAR BOTÓN AÑADIR A COLA
        val fabCola = findViewById<FloatingActionButton>(R.id.fabColaDetalle)
        fabCola.setOnClickListener {
            if (musicaService != null && cancionActual != null) {
                val resID = resources.getIdentifier(cancionActual!!.recursoRaw, "raw", packageName)
                if (resID != 0) {
                    val exito = musicaService!!.agregarACola(resID)
                    if (exito) {
                        Toast.makeText(this, "Añadida a la cola", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Ya en cola o llena", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Conectando servicio...", Toast.LENGTH_SHORT).show()
            }
        }

        // GESTOS DESLIZAR (SWIPE DOWN)
        gestureDetector = GestureDetectorCompat(this, SwipeListener())

        // Listener en el fondo para minimizar deslizando
        val rootLayout = findViewById<View>(R.id.rootLayoutDetalle)
        rootLayout.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            // IMPORTANTE: Devolvemos true solo si el gesto se consumió, si no false.
            // Pero aquí devolvemos true para que el rootLayout siga recibiendo eventos de movimiento
            // sin bloquear los clicks de los hijos (que están encima).
            true
        }

        iniciarGiroTecnologicoPermanente()
    }

    // --- DETECTOR DE GESTOS ---
    // NO SOBREESCRIBIR dispatchTouchEvent SI NO ES NECESARIO, A VECES CAUSA CONFLICTOS CON CLICKS
    // Si usas el OnTouchListener en el rootLayout suele ser suficiente.

    private inner class SwipeListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val diffY = e2.y - e1.y

            // Solo nos interesa hacia abajo y rápido
            if (diffY > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                finish() // Minimizar (cerrar actividad)
                return true
            }
            return false
        }
    }

    // --- RESTO DE MÉTODOS ---
    override fun onMusicaServiceConnected() {
        super.onMusicaServiceConnected()
        if (musicaService != null && cancionActual != null) {
            val nombreRawActual = cancionActual!!.recursoRaw
            val nombreSonando = musicaService!!.getNombreCancion()
            val estaSonando = musicaService!!.isPlaying()
            val fabPlay = findViewById<FloatingActionButton>(R.id.fabPlayDetalle)

            if (nombreRawActual == nombreSonando && estaSonando) {
                esReproduciendo = true
                fabPlay.setImageResource(android.R.drawable.ic_media_pause)
                animarDisco(true)
            } else {
                esReproduciendo = false
                fabPlay.setImageResource(R.drawable.boton_de_play)
                animarDisco(false)
            }
        }
    }

    private fun iniciarGiroTecnologicoPermanente() {
        val ivTecnologico = findViewById<View>(R.id.ivCirculoGiratorio)
        animadorTech = ObjectAnimator.ofFloat(ivTecnologico, "rotation", 0f, 360f).apply {
            duration = 8000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun cargarDatos(cancion: Cancion) {
        findViewById<TextView>(R.id.tvDetalleNombre).text = cancion.titulo
        findViewById<TextView>(R.id.tvDetalleDescripcion).text = cancion.artista
        findViewById<TextView>(R.id.tvDetalleMetadata).text = "${cancion.genero} • ${cancion.anioLanzamiento}"
        findViewById<TextView>(R.id.tvDuracion).text = "⏱ ${cancion.duracion}"

        val ivImagen = findViewById<ImageView>(R.id.ivDetalleImagen)
        val resourceId = resources.getIdentifier(cancion.imagenUri, "drawable", packageName)
        if (resourceId != 0) ivImagen.setImageResource(resourceId)

        findViewById<TextView>(R.id.tvTipo1).text = "${cancion.visitas} PLAYS"
        findViewById<TextView>(R.id.tvTipo2).text = "${cancion.meGusta} LIKES"
    }

    private fun toggleReproduccion() {
        esReproduciendo = !esReproduciendo
        val fabPlay = findViewById<FloatingActionButton>(R.id.fabPlayDetalle)

        if (esReproduciendo) {
            fabPlay.setImageResource(android.R.drawable.ic_media_pause)
            if (cancionActual != null) {
                val resID = resources.getIdentifier(cancionActual!!.recursoRaw, "raw", packageName)
                if (resID != 0) {
                    val intentMusic = Intent(this, MusicaService::class.java)
                    intentMusic.action = "CAMBIAR_CANCION"
                    intentMusic.putExtra("ID_CANCION", resID)
                    startService(intentMusic)
                    val db = AdminSQL(this)
                    db.sumarVisita(cancionActual!!.id)
                }
            }
            animarDisco(true)
        } else {
            fabPlay.setImageResource(R.drawable.boton_de_play)
            val intentMusic = Intent(this, MusicaService::class.java)
            intentMusic.action = "PAUSE"
            startService(intentMusic)
            animarDisco(false)
        }
    }

    private fun animarDisco(salir: Boolean) {
        val ivVinilo = findViewById<View>(R.id.ivVinilo)
        val ivTecnologico = findViewById<View>(R.id.ivCirculoGiratorio)
        val ivCaratula = findViewById<View>(R.id.ivDetalleImagen)
        val containerDisco = findViewById<View>(R.id.containerDisco)

        val duracionTransicion = 1500L
        val interpoladorSalida = OvershootInterpolator(1.2f)

        if (salir) {
            ivTecnologico.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(duracionTransicion).start()
            ivVinilo.scaleX = 0.7f; ivVinilo.scaleY = 0.7f; ivVinilo.alpha = 0f
            ivVinilo.animate().alpha(1f).scaleX(1.15f).scaleY(1.15f)
                .setDuration(duracionTransicion).setInterpolator(interpoladorSalida).start()
            ivCaratula.animate().scaleX(1.05f).scaleY(1.05f)
                .setDuration(duracionTransicion).setInterpolator(interpoladorSalida).start()

            if (animadorGiroVinilo == null) {
                animadorGiroVinilo = ObjectAnimator.ofFloat(containerDisco, "rotation", 0f, 360f)
                animadorGiroVinilo?.duration = 4000
                animadorGiroVinilo?.repeatCount = ObjectAnimator.INFINITE
                animadorGiroVinilo?.interpolator = LinearInterpolator()
            }
            if (animadorGiroVinilo?.isRunning == false) animadorGiroVinilo?.start() else animadorGiroVinilo?.resume()

        } else {
            ivTecnologico.animate().alpha(0.9f).scaleX(1f).scaleY(1f).setDuration(duracionTransicion).start()
            ivVinilo.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f)
                .setDuration(duracionTransicion).setInterpolator(AccelerateDecelerateInterpolator()).start()
            ivCaratula.animate().scaleX(1f).scaleY(1f).setDuration(duracionTransicion).start()
            animadorGiroVinilo?.pause()
        }
    }
    // Sobreescribimos el método que se llama cuando el servicio avisa de cambios
    override fun actualizarMiniPlayer() {
        // 1. Primero dejamos que la clase padre (MusicBaseActivity) actualice el MiniPlayer de abajo
        super.actualizarMiniPlayer()

        // 2. Lógica exclusiva de Detalle: Controlar el Vinilo Grande
        if (musicaService != null && cancionActual != null) {
            val nombreSonando = musicaService!!.getNombreCancion()
            val estaSonando = musicaService!!.isPlaying()
            val fabPlay = findViewById<FloatingActionButton>(R.id.fabPlayDetalle)

            // Comprobamos si la canción que suena es la que estamos viendo
            // cancionActual se carga en el onCreate, así que usamos esa referencia
            val esLaMisma = (nombreSonando == cancionActual!!.recursoRaw)

            if (esLaMisma) {
                // Si es la misma, actualizamos el botón de play grande según el estado real
                if (estaSonando) {
                    esReproduciendo = true
                    fabPlay.setImageResource(android.R.drawable.ic_media_pause)
                    animarDisco(true) // Gira vinilo
                } else {
                    esReproduciendo = false
                    fabPlay.setImageResource(R.drawable.boton_de_play)
                    animarDisco(false) // Para vinilo
                }
            } else {
                // Si NO es la misma (ej: saltó a la siguiente en la cola),
                // en esta pantalla mostramos que NO está sonando esta canción específica.
                esReproduciendo = false
                fabPlay.setImageResource(R.drawable.boton_de_play)
                animarDisco(false) // Para vinilo
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        animadorGiroVinilo?.cancel()
        animadorTech?.cancel()
    }
}