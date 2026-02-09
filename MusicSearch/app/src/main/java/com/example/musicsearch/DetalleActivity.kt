package com.example.musicsearch

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DetalleActivity : MusicBaseActivity() {

    private var cancionActual: Cancion? = null
    private var esReproduciendo = false

    // Animadores
    private var animadorGiroVinilo: ObjectAnimator? = null
    private var animadorTech: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle)

        // 2. Recuperar ID y cargar datos
        val idCancion = intent.getIntExtra("ID_CANCION", -1)
        if (idCancion != -1) {
            val db = AdminSQL(this)
            cancionActual = db.obtenerCancionPorId(idCancion)
            if (cancionActual != null) {
                cargarDatos(cancionActual!!)
            }
        }

        // 3. Configurar Botones
        val fabPlay = findViewById<FloatingActionButton>(R.id.fabPlayDetalle)
        fabPlay.setOnClickListener { toggleReproduccion() }

        findViewById<View>(R.id.btnVolver).setOnClickListener {
            finish()
        }

        // 4. Iniciar giro de fondo (siempre activo)
        iniciarGiroTecnologicoPermanente()
    }

    // --- NUEVO: SINCRONIZACIÓN AL CONECTARSE AL SERVICIO ---
    override fun onMusicaServiceConnected() {
        super.onMusicaServiceConnected()

        // Verificamos si la canción de esta pantalla es la que está sonando
        if (musicaService != null && cancionActual != null) {
            val nombreRawActual = cancionActual!!.recursoRaw
            val nombreSonando = musicaService!!.getNombreCancion() // Método nuevo del servicio
            val estaSonando = musicaService!!.isPlaying()

            if (nombreRawActual == nombreSonando && estaSonando) {
                // SÍ, es esta canción -> Ponemos estado PLAY
                esReproduciendo = true
                val fabPlay = findViewById<FloatingActionButton>(R.id.fabPlayDetalle)
                fabPlay.setImageResource(android.R.drawable.ic_media_pause)

                // Activamos vinilo (sin animación lenta al entrar, directo)
                // O con animación si prefieres que se vea el efecto al abrir
                animarDisco(true)
            } else {
                // NO, está parada o es otra -> Estado PAUSE
                esReproduciendo = false
                val fabPlay = findViewById<FloatingActionButton>(R.id.fabPlayDetalle)
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
            // --- PLAY ---
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
            // --- PAUSE ---
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
            // >>> MODO PLAY: Vinilo sale <<<

            // 1. Tecnológico se va (Zoom out)
            ivTecnologico.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(duracionTransicion).start()

            // 2. Vinilo aparece creciendo
            ivVinilo.scaleX = 0.7f
            ivVinilo.scaleY = 0.7f
            ivVinilo.alpha = 0f
            ivVinilo.animate().alpha(1f).scaleX(1.15f).scaleY(1.15f)
                .setDuration(duracionTransicion).setInterpolator(interpoladorSalida).start()

            // 3. Carátula pop
            ivCaratula.animate().scaleX(1.05f).scaleY(1.05f)
                .setDuration(duracionTransicion).setInterpolator(interpoladorSalida).start()

            // 4. Girar
            if (animadorGiroVinilo == null) {
                animadorGiroVinilo = ObjectAnimator.ofFloat(containerDisco, "rotation", 0f, 360f)
                animadorGiroVinilo?.duration = 4000
                animadorGiroVinilo?.repeatCount = ObjectAnimator.INFINITE
                animadorGiroVinilo?.interpolator = LinearInterpolator()
            }
            if (!animadorGiroVinilo!!.isRunning) animadorGiroVinilo?.start() else animadorGiroVinilo?.resume()

        } else {
            // >>> MODO PAUSE: Vinilo se esconde <<<

            // 1. Tecnológico vuelve (Zoom in)
            ivTecnologico.animate().alpha(0.9f).scaleX(1f).scaleY(1f).setDuration(duracionTransicion).start()

            // 2. Vinilo se encoge
            ivVinilo.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f)
                .setDuration(duracionTransicion).setInterpolator(AccelerateDecelerateInterpolator()).start()

            // 3. Carátula normal
            ivCaratula.animate().scaleX(1f).scaleY(1f).setDuration(duracionTransicion).start()

            // 4. Parar giro
            animadorGiroVinilo?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        animadorGiroVinilo?.cancel()
        animadorTech?.cancel()
    }
}