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
// DetalleActivity: Pantalla que muestra la información completa de una canción.
// Incluye animaciones de vinilo, control de reproducción y gestos para cerrar la vista.
class DetalleActivity : MusicBaseActivity() {
    private var cancionActual: Cancion? = null
    private var esReproduciendo = false

    // Animadores: Controlan la rotación del disco y los efectos tecnológicos de fondo.
    private var animadorGiroVinilo: ObjectAnimator? = null
    private var animadorTech: ObjectAnimator? = null

    // Detector de gestos: Permite cerrar la actividad deslizando hacia abajo (Swipe Down).
    private lateinit var gestureDetector: GestureDetectorCompat

    // Referencias a los botones flotantes (FAB) para sincronizarlos con el MiniPlayer.
    private lateinit var fabPlay: FloatingActionButton
    private lateinit var fabCola: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle)

        // CONFIGURACIÓN BOTÓN ATRÁS: Finaliza la actividad para volver al catálogo.
        val btnAtras = findViewById<View>(R.id.btnVolver)
        btnAtras.setOnClickListener { finish() }

        // CARGA DE DATOS: Recuperamos el ID enviado por el Intent y buscamos en la DB.
        val idCancion = intent.getIntExtra("ID_CANCION", -1)
        if (idCancion != -1) {
            val db = AdminSQL(this)
            cancionActual = db.obtenerCancionPorId(idCancion)
            if (cancionActual != null) {
                cargarDatos(cancionActual!!)
            }
        }

        // BOTÓN PLAY: Lanza o pausa la reproducción de la canción actual.
        fabPlay = findViewById(R.id.fabPlayDetalle)
        fabPlay.setOnClickListener { toggleReproduccion() }

        // BOTÓN COLA: Añade la canción actual a la lista de espera del servicio.
        fabCola = findViewById(R.id.fabColaDetalle)
        fabCola.setOnClickListener {
            // "Guarda" de seguridad: Solo funciona si el servicio está activo y hay música sonando.
            if (musicaService != null && cancionActual != null && musicaService!!.getNombreCancion() != null) {
                val resID = resources.getIdentifier(cancionActual!!.recursoRaw, "raw", packageName)
                if (resID != 0) {
                    val exito = musicaService!!.agregarACola(resID)
                    if (exito) {
                        Toast.makeText(this, "✅ Añadida a la cola", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "⚠️ La cola está llena", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Debes reproducir algo primero para usar la cola", Toast.LENGTH_SHORT).show()
            }
        }

        // GESTOS: Configuramos el detector de movimientos sobre el layout principal.
        gestureDetector = GestureDetectorCompat(this, SwipeListener())
        val rootLayout = findViewById<View>(R.id.rootLayoutDetalle)
        rootLayout.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true // El layout sigue escuchando el toque.
        }

        // EFECTOS VISUALES: Inicia el giro decorativo de la interfaz tecnológica.
        iniciarGiroTecnologicoPermanente()
    }

    // =========================================================
    //  SINCRONIZACIÓN CON EL MOVIMIENTO DEL MINI PLAYER
    // =========================================================

    // Mueve los botones verticalmente al mismo tiempo que el usuario arrastra el MiniPlayer.
    override fun moverElementosHijos(translationY: Float) {
        if (::fabPlay.isInitialized) fabPlay.translationY = translationY
        if (::fabCola.isInitialized) fabCola.translationY = translationY
    }

    // Aplica una animación suave a los botones cuando el MiniPlayer se asienta (abierto o cerrado).
    override fun animarElementosHijos(destinoY: Float) {
        val animadores = listOf(fabPlay, fabCola)
        animadores.forEach { fab ->
            if (::fabPlay.isInitialized) {
                fab.animate()
                    .translationY(destinoY)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(0.8f))
                    .start()
            }
        }
    }

    // =========================================================
    //  DETECTOR DE GESTOS (SWIPE PARA CERRAR)
    // =========================================================

    private inner class SwipeListener : GestureDetector.SimpleOnGestureListener() {
        private val UMBRAL_DESLIZAMIENTO = 100
        private val UMBRAL_VELOCIDAD = 100

        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
            if (e1 == null) return false
            val diffY = e2.y - e1.y
            // Si el gesto es hacia abajo y con suficiente velocidad, cerramos la pantalla.
            if (diffY > UMBRAL_DESLIZAMIENTO && abs(vY) > UMBRAL_VELOCIDAD) {
                finish()
                return true
            }
            return false
        }
    }

    // =========================================================
    //  LÓGICA DE REPRODUCCIÓN Y ANIMACIONES DEL DISCO
    // =========================================================

    // Se conecta al estado del servicio cuando la actividad arranca o se vincula.
    override fun onMusicaServiceConnected() {
        super.onMusicaServiceConnected()
        actualizarMiniPlayer() // Sincroniza el estado del disco con lo que está sonando.
    }

    // Rotación constante del fondo circular para dar un toque futurista.
    private fun iniciarGiroTecnologicoPermanente() {
        val ivTecnologico = findViewById<View>(R.id.ivCirculoGiratorio)
        animadorTech = ObjectAnimator.ofFloat(ivTecnologico, "rotation", 0f, 360f).apply {
            duration = 8000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    // Rellena la UI con los metadatos de la canción (título, artista, género...).
    private fun cargarDatos(cancion: Cancion) {
        findViewById<TextView>(R.id.tvDetalleNombre).text = cancion.titulo
        findViewById<TextView>(R.id.tvDetalleDescripcion).text = cancion.artista
        findViewById<TextView>(R.id.tvDetalleMetadata).text = "${cancion.genero} • ${cancion.anioLanzamiento}"
        findViewById<TextView>(R.id.tvDuracion).text = "⏱ ${cancion.duracion}"

        val ivImagen = findViewById<ImageView>(R.id.ivDetalleImagen)
        val resId = resources.getIdentifier(cancion.imagenUri, "drawable", packageName)
        if (resId != 0) ivImagen.setImageResource(resId)

        findViewById<TextView>(R.id.tvTipo1).text = "${cancion.visitas} PLAYS"
        findViewById<TextView>(R.id.tvTipo2).text = "${cancion.meGusta} LIKES"
    }

    // Gestiona el cambio entre Play/Pause avisando al MusicaService.
    private fun toggleReproduccion() {
        esReproduciendo = !esReproduciendo

        if (esReproduciendo) {
            fabPlay.setImageResource(android.R.drawable.ic_media_pause)
            if (cancionActual != null) {
                val resID = resources.getIdentifier(cancionActual!!.recursoRaw, "raw", packageName)
                if (resID != 0) {
                    val intent = Intent(this, MusicaService::class.java).apply {
                        action = "CAMBIAR_CANCION"
                        putExtra("ID_CANCION", resID)
                    }
                    startService(intent)
                    AdminSQL(this).sumarVisita(cancionActual!!.id)
                }
            }
        } else {
            fabPlay.setImageResource(R.drawable.boton_de_play)
            startService(Intent(this, MusicaService::class.java).apply { action = "PAUSE" })
        }
    }

    // Controla la coreografía de las animaciones: el vinilo sale, la carátula se expande y el disco gira.
    private fun animarDisco(reproducir: Boolean) {
        val ivVinilo = findViewById<View>(R.id.ivVinilo)
        val ivTecnologico = findViewById<View>(R.id.ivCirculoGiratorio)
        val ivCaratula = findViewById<View>(R.id.ivDetalleImagen)
        val containerDisco = findViewById<View>(R.id.containerDisco)

        val tiempo = 1500L
        val rebote = OvershootInterpolator(1.2f)

        if (reproducir) {
            // El disco "despierta": el vinilo asoma por un lado.
            ivTecnologico.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(tiempo).start()
            ivVinilo.scaleX = 0.7f; ivVinilo.alpha = 0f
            ivVinilo.animate().alpha(1f).scaleX(1.15f).scaleY(1.15f)
                .setDuration(tiempo).setInterpolator(rebote).start()
            ivCaratula.animate().scaleX(1.05f).scaleY(1.05f)
                .setDuration(tiempo).setInterpolator(rebote).start()

            if (animadorGiroVinilo == null) {
                animadorGiroVinilo = ObjectAnimator.ofFloat(containerDisco, "rotation", 0f, 360f).apply {
                    duration = 4000
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                }
            }
            if (animadorGiroVinilo?.isRunning == false) animadorGiroVinilo?.start() else animadorGiroVinilo?.resume()
        } else {
            // Todo vuelve al estado de reposo.
            ivTecnologico.animate().alpha(0.9f).scaleX(1f).scaleY(1f).setDuration(tiempo).start()
            ivVinilo.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(tiempo).start()
            ivCaratula.animate().scaleX(1f).scaleY(1f).setDuration(tiempo).start()
            animadorGiroVinilo?.pause()
        }
    }

    // Refresca la interfaz de Detalle cada vez que el servicio notifica un cambio (Play/Pause/Siguiente).
    override fun actualizarMiniPlayer() {
        super.actualizarMiniPlayer() // Actualizamos primero el reproductor inferior base.

        if (musicaService != null && cancionActual != null) {
            val nombreSonando = musicaService!!.getNombreCancion()
            val estaSonando = musicaService!!.isPlaying()
            val esLaMisma = (nombreSonando == cancionActual!!.recursoRaw)

            // --- LÓGICA DE CONTROL DEL BOTÓN COLA ---
            // Solo habilitamos visualmente el botón de cola si hay música activa en el servicio.
            if (nombreSonando != null) {
                fabCola.alpha = 1.0f
                fabCola.isEnabled = true
            } else {
                fabCola.alpha = 0.3f
                fabCola.isEnabled = false
            }

            // --- LÓGICA DEL DISCO GRANDE ---
            if (esLaMisma && estaSonando) {
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

    override fun onDestroy() {
        super.onDestroy()
        animadorGiroVinilo?.cancel()
        animadorTech?.cancel()
    }
}