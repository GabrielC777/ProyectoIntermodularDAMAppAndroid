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

    // Referencias a los botones para moverlos con el swipe
    private lateinit var fabPlay: FloatingActionButton
    private lateinit var fabCola: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle)

        // CONFIGURAR BOTÓN ATRÁS
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

        // CONFIGURAR BOTÓN PLAY (Inicializamos la variable global)
        fabPlay = findViewById(R.id.fabPlayDetalle)
        fabPlay.setOnClickListener { toggleReproduccion() }

        // CONFIGURAR BOTÓN AÑADIR A COLA (Inicializamos la variable global)
        fabCola = findViewById(R.id.fabColaDetalle)
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
            // IMPORTANTE: Devolvemos true para que el rootLayout siga recibiendo eventos
            true
        }

        iniciarGiroTecnologicoPermanente()
    }

    // --- MÉTODOS DE SINCRONIZACIÓN CON EL MINIPLAYER (NUEVO) ---
    // Estos métodos vienen de MusicBaseActivity y permiten que los botones
    // sigan el movimiento del MiniPlayer cuando lo deslizas.

    override fun moverElementosHijos(translationY: Float) {
        // Movemos los botones la misma distancia que el player en tiempo real
        if (::fabPlay.isInitialized) fabPlay.translationY = translationY
        if (::fabCola.isInitialized) fabCola.translationY = translationY
    }

    override fun animarElementosHijos(destinoY: Float) {
        // Animamos los botones al soltar el player (efecto rebote)
        if (::fabPlay.isInitialized) {
            fabPlay.animate()
                .translationY(destinoY)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()
        }
        if (::fabCola.isInitialized) {
            fabCola.animate()
                .translationY(destinoY)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()
        }
    }
    // -------------------------------------------------------------


    // --- DETECTOR DE GESTOS ---
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

            // Usamos la variable de clase ya inicializada
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
        else ivImagen.setImageResource(R.drawable.ic_launcher_foreground) // Protección por si acaso

        findViewById<TextView>(R.id.tvTipo1).text = "${cancion.visitas} PLAYS"
        findViewById<TextView>(R.id.tvTipo2).text = "${cancion.meGusta} LIKES"
    }

    private fun toggleReproduccion() {
        esReproduciendo = !esReproduciendo

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

            // Usamos la variable de clase (que ya está inicializada en onCreate)
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
                // Si NO es la misma, paramos todo
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