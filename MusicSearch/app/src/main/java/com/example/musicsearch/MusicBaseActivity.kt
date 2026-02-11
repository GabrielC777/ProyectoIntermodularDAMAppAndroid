package com.example.musicsearch

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlin.math.abs

// MusicBaseActivity actúa como la clase base para todas las actividades que necesitan funcionalidad de reproducción de música o transiciones.
// Hereda de AppCompatActivity para proporcionar compatibilidad con versiones antiguas de Android.
open class MusicBaseActivity : AppCompatActivity() {

    // =========================================================
    //  VARIABLES GLOBALES
    // =========================================================
    // Referencia al servicio de música para controlar la reproducción.
    protected var musicaService: MusicaService? = null
    // Bandera para saber si la actividad está vinculada al servicio.
    protected var isServiceBound = false

    // UI MiniPlayer: Elementos de la interfaz del reproductor minimizado.
    private var miniPlayerView: View? = null// Elemento general
    private var miniSeekBar: SeekBar? = null // Slider para la progresión de la canción

    // Handler para actualizar la interfaz de usuario desde el hilo principal.
    private val handlerUI = Handler(Looper.getMainLooper())

    // Bandera para evitar conflictos entre la actualización automática del SeekBar y la interacción del usuario.
    private var isUserSeeking = false

    // Variables para la cola de reproducción.
    private var isColaVisible = false
    private lateinit var recyclerCola: RecyclerView

    // UI Transiciones, menú y animaciones: Elementos para la navegación y efectos visuales.
    private var animadorVinilo: ObjectAnimator? = null // Animación de rotación para la imagen del álbum.
    private lateinit var layoutTransicion: ConstraintLayout // Contenedor para la animación de transición entre pantallas.
    private lateinit var cortinaVerde: View // Parte de la animación de transición.
    private lateinit var cortinaBlanca: View // Parte de la animación de transición.
    protected lateinit var btnFlotanteCentral: FrameLayout // Botón principal del menú flotante.
    protected lateinit var fabOpcion1: MaterialButton // Opción 1 del menú.
    protected lateinit var fabOpcion2: MaterialButton // Opción 2 del menú.
    protected lateinit var fabOpcion3: MaterialButton // Opción 3 del menú.
    protected var isMenuAbierto = false // Estado del menú flotante.
    private var tvTituloHeader: TextView? = null // Título de la pantalla actual. ///////////Lo mas seguro borrar
    private val MARGEN_SEGURIDAD_DP = 60f // Margen para evitar que elementos se superpongan con la barra de navegación o estado.

    // =========================================================
    //  CONEXIÓN CON SERVICIO DE MÚSICA
    // =========================================================
    // Receptor de broadcasts para actualizar el MiniPlayer cuando ocurren eventos en el servicio.
    private val musicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "EVENTO_ACTUALIZAR_MINIPLAYER") {
                actualizarMiniPlayer()
            }
        }
    }

    // Objeto para gestionar la conexión y desconexión con el servicio de música.
    private val connection = object : ServiceConnection {
        //Metodo que se ejecuta cuando la conexion Activity a Servicio tiene exito
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            //Binder es el cable que nos conecta con el servicio génerico
            //Como es génerico lo tenemos que castear para conseguir el del servicio que nos interesa
            val binder = service as MusicaService.LocalBinder
            musicaService = binder.getService()
            isServiceBound = true // marcamos como servicio conectado

            // Callback para notificar a las clases hijas que el servicio está listo.
            onMusicaServiceConnected()

            // Inicializar la interfaz del reproductor una vez conectado el servicio.
            setupMiniPlayer()
            actualizarMiniPlayer()
            iniciarActualizacionSlider()
        }

        // Se ejecuta si el servicio se desconecta inesperadamente.
        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
            musicaService = null
        }
    }

    // Método hook para que las subclases realicen acciones al conectarse el servicio.
    protected open fun onMusicaServiceConnected() {}

    // =========================================================
    //  LÓGICA MINI PLAYER
    // =========================================================
    // Configura la vista y el comportamiento del MiniPlayer.
    protected fun setupMiniPlayer() {
        // -----------------------------------------------------------
        // 1. OBTENCIÓN DE REFERENCIAS Y VALIDACIÓN
        // -----------------------------------------------------------
        miniPlayerView = findViewById(R.id.layoutMiniPlayer)
        if (miniPlayerView == null) return // Si no existe la vista, salimos.

        // Elevación para que el reproductor flote sobre otros elementos.
        miniPlayerView!!.elevation = 105f

        // Referencias a los componentes internos del MiniPlayer.
        val cabecera = miniPlayerView!!.findViewById<View>(R.id.cabeceraPlayer)
        val btnVerCola = miniPlayerView!!.findViewById<ImageButton>(R.id.btnVerCola)
        val btnPlay = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniPlay)
        val btnNext = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniNext)
        val btnPrev = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniPrev)
        miniSeekBar = miniPlayerView!!.findViewById(R.id.miniSeekBar)
        recyclerCola = miniPlayerView!!.findViewById(R.id.recyclerCola)

        // Configuración del RecyclerView para la lista de reproducción.
        if (recyclerCola != null) {
            recyclerCola.layoutManager = LinearLayoutManager(this)
        }

        // -----------------------------------------------------------
        // 2. CÁLCULO DE DIMENSIONES Y ESTADO INICIAL
        // -----------------------------------------------------------
        // Calcula la altura para ocultar el reproductor (la lista).
        val alturaDesplazamiento = 250f * resources.displayMetrics.density

        // Estado inicial: Reproductor minimizado (empujado hacia abajo).
        miniPlayerView?.translationY = alturaDesplazamiento
        moverMenuCompleto(0f)
        isColaVisible = false

        // -----------------------------------------------------------
        // 3. LÓGICA DE ARRASTRE (SWIPE)
        // -----------------------------------------------------------
        // Listener para manejar el gesto de deslizar el MiniPlayer.
        cabecera.setOnTouchListener(object : View.OnTouchListener {
            var startRawY = 0f    // Posición inicial del dedo en pantalla.
            var startViewY = 0f   // Posición inicial de la vista.
            var lastDiff = 0f     // Diferencia de movimiento para detectar la dirección.

            override fun onTouch(v: View?, event: android.view.MotionEvent?): Boolean {
                when (event?.action) {
                    // A) EL DEDO TOCA LA PANTALLA
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startRawY = event.rawY
                        startViewY = miniPlayerView!!.translationY
                        // Cierra el menú flotante si está abierto al interactuar con el reproductor.
                        if (isMenuAbierto) cerrarMenuAbanicoRapido()
                        return true
                    }

                    // B) EL DEDO SE MUEVE (ARRASTRE)
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - startRawY
                        var newY = startViewY + dy
                        lastDiff = dy

                        // Limita el movimiento dentro del rango permitido (0 a alturaDesplazamiento).
                        if (newY < 0f) newY = 0f
                        if (newY > alturaDesplazamiento) newY = alturaDesplazamiento

                        // Mueve el MiniPlayer.
                        miniPlayerView!!.translationY = newY

                        // Mueve el menú flotante en relación al reproductor.
                        val offsetMenu = newY - alturaDesplazamiento
                        moverMenuCompleto(offsetMenu)
                        moverElementosHijos(offsetMenu) // Mueve elementos específicos de la actividad hija (Para Detalles BtnPlayPause, Añadir lista)
                        return true
                    }

                    // C) EL DEDO SE LEVANTA (DECISIÓN FINAL)
                    android.view.MotionEvent.ACTION_UP -> {
                        val currentY = miniPlayerView!!.translationY
                        val umbral = alturaDesplazamiento / 2
                        var abrir = false

                        // Determina si abrir o cerrar el reproductor basado en la velocidad o posición final.
                        if (abs(lastDiff) > 10) {
                            if (lastDiff < 0) abrir = true // Swipe rápido hacia arriba.
                        } else {
                            if (currentY < umbral) abrir = true // Arrastre lento superando la mitad.
                        }

                        // Anima a la posición final (abierto o cerrado).
                        if (abrir) {
                            // ABRIR
                            animarPlayerYBtnFlotante(0f, -alturaDesplazamiento)
                            isColaVisible = true
                            actualizarListaCola()
                            btnVerCola?.animate()?.rotation(0f)?.start()
                        } else {
                            // CERRAR
                            animarPlayerYBtnFlotante(alturaDesplazamiento, 0f)
                            isColaVisible = false
                            btnVerCola?.animate()?.rotation(180f)?.start()
                        }

                        lastDiff = 0f
                        return true
                    }
                }
                return false
            }
        })

        // -----------------------------------------------------------
        // 4. CONFIGURACIÓN DE BOTONES (PLAY, NEXT, PREV, COLA)
        // -----------------------------------------------------------

        // Configuración de listeners para los controles de reproducción.
        btnPlay?.setOnClickListener {
            if (musicaService != null) {
                val action = if (musicaService!!.isPlaying()) "PAUSE" else "RESUME"
                val intent = Intent(this, MusicaService::class.java).apply {this.action = action }
                startService(intent)
            }
        }

        btnNext?.setOnClickListener {
            if (musicaService != null) {
                startService(Intent(this, MusicaService::class.java).apply { action = "ACTION_NEXT" })
            }
        }

        btnPrev?.setOnClickListener {
            if (musicaService != null) {
                startService(Intent(this, MusicaService::class.java).apply { action = "ACTION_PREV" })
            }
        }

        //Abrir o cerrar la cola pulsando un botón
        btnVerCola?.setOnClickListener {
            if (isColaVisible) {
                animarPlayerYBtnFlotante(alturaDesplazamiento, 0f)
                btnVerCola.animate().rotation(180f).start()
            } else {
                animarPlayerYBtnFlotante(0f, -alturaDesplazamiento)
                btnVerCola.animate().rotation(0f).start()
                actualizarListaCola()
            }
            isColaVisible = !isColaVisible
        }

        // Listener del SeekBar para permitir al usuario cambiar la posición(el tiempo) de la canción.
        miniSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isUserSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                if (musicaService != null && seekBar != null) {
                    val total = musicaService!!.getDuracionTotal()
                    if (total > 0) {
                        musicaService!!.seekTo(seekBar.progress)
                    }
                }
            }
        })

        // Evita que los clics en el reproductor pasen a las vistas de fondo.
        miniPlayerView!!.isClickable = true
        miniPlayerView!!.setOnClickListener {
        }
    }

    // --- HELPER 1: MOVER TODO FÍSICAMENTE ---
    // Mueve el menú flotante y sus opciones verticalmente.
    private fun moverMenuCompleto(offset: Float) {
        if (::btnFlotanteCentral.isInitialized) btnFlotanteCentral.translationY = offset
        if (::fabOpcion1.isInitialized) fabOpcion1.translationY = offset
        if (::fabOpcion2.isInitialized) fabOpcion2.translationY = offset
        if (::fabOpcion3.isInitialized) fabOpcion3.translationY = offset
    }

    // --- HELPER 2: ANIMAR TODO ---
    // Anima el movimiento del reproductor y el menú flotante.
    private fun animarPlayerYBtnFlotante(destinoPlayer: Float, destinoMenu: Float) {
        //Animación para miniplayer
        miniPlayerView!!.animate()
            .translationY(destinoPlayer) //Donde va
            .setDuration(300) //Cuanto tarda
            .setInterpolator(OvershootInterpolator(0.8f)) //El estilo
            .start()
        //Animación para botón flotante
        if (::btnFlotanteCentral.isInitialized) {
            btnFlotanteCentral.animate()
                .translationY(destinoMenu)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()

            val views = listOf(fabOpcion1, fabOpcion2, fabOpcion3)
            views.forEach {
                if (it != null && ::fabOpcion1.isInitialized) {
                    it.animate()
                        .translationY(destinoMenu)
                        .setDuration(300)
                        .setInterpolator(OvershootInterpolator(0.8f))
                        .start()
                }
            }
        }
        // Anima elementos específicos de la actividad hija.
        animarElementosHijos(destinoMenu)
    }

    // Métodos para ser sobrescritos por actividades hijas para mover/animar sus propios elementos.
    protected open fun moverElementosHijos(translationY: Float) {
        // Por defecto no hace nada
    }

    protected open fun animarElementosHijos(destinoY: Float) {
        // Por defecto no hace nada
    }

    // Cierra el menú flotante sin animación compleja
    private fun cerrarMenuAbanicoRapido() {
        if (!::btnFlotanteCentral.isInitialized) return

        isMenuAbierto = false
        btnFlotanteCentral.animate()
            .rotation(0f)
            .setDuration(200)
            .start()
        val currentY = btnFlotanteCentral.translationY

        val views = listOf(fabOpcion1, fabOpcion2, fabOpcion3)
        views.forEach {
            if (it != null && ::fabOpcion1.isInitialized) {
                it.visibility = View.INVISIBLE
                it.translationX = 0f
                it.translationY = currentY
            }
        }
    }

    // Actualiza la interfaz del MiniPlayer con la información de la canción actual.
    protected open fun actualizarMiniPlayer() {
        if (miniPlayerView == null || musicaService == null) return

        val nombreRaw = musicaService!!.getNombreCancion()
        val isPlaying = musicaService!!.isPlaying()

        // Referencias a las vistas.
        val tvTitulo = miniPlayerView!!.findViewById<TextView>(R.id.tvMiniTitulo)
        val tvArtista = miniPlayerView!!.findViewById<TextView>(R.id.tvMiniArtista)
        val ivImagen = miniPlayerView!!.findViewById<ImageView>(R.id.ivMiniImagen)
        val btnPlay = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniPlay)
        val btnNext = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniNext)
        val btnPrev = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniPrev)

        // Configura la animación de rotación del vinilo.
        if (animadorVinilo == null && ivImagen != null) {
            //.ofFloat : crear una animacion que controle número decimal
            animadorVinilo = ObjectAnimator.ofFloat(ivImagen,
                "rotation",
                0f,
                360f)
                .apply {duration = 4000
                        repeatCount = ObjectAnimator.INFINITE
                        interpolator = LinearInterpolator()
            }
        }

        if (nombreRaw != null) {
            // Muestra el reproductor si hay una canción seleccionada.
            if (miniPlayerView!!.visibility != View.VISIBLE) {
                miniPlayerView!!.visibility = View.VISIBLE
            }
            if (isColaVisible) actualizarListaCola()

            // Configura el máximo del SeekBar.
            val duracion = musicaService!!.getDuracionTotal()
            if (duracion > 0) {
                miniSeekBar?.max = duracion
            }

            // Recupera metadatos de la canción desde la base de datos.
            val db = AdminSQL(this)
            val cancion = db.obtenerTodasLasCanciones().find {
                it.recursoRaw == nombreRaw

            }
            //Asignamos los nuevos datos a MiniPlayer
            if (cancion != null) {
                tvTitulo.text = cancion.titulo
                tvTitulo.isSelected = true // Habilita el efecto marquesina.
                tvArtista.text = cancion.artista
                //Obtenemos la imagen
                val resId = resources.getIdentifier(
                    cancion.imagenUri,
                    "drawable",
                    packageName)

                if (resId != 0) ivImagen.setImageResource(resId)
            }

            // Actualiza el estado del botón Play/Pause y la animación del vinilo.
            if (isPlaying) {
                btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                iniciarActualizacionSlider()
                if (animadorVinilo?.isPaused == true) animadorVinilo?.resume()
                else if (animadorVinilo?.isRunning == false) animadorVinilo?.start()
            } else {
                btnPlay.setImageResource(R.drawable.boton_de_play)
                if (animadorVinilo?.isRunning == true) animadorVinilo?.pause()
            }

            // Controla  los botones Next y Prev.
            val hayCola = musicaService!!.getCola().isNotEmpty()
            //Opacidad
            btnNext.alpha = if (hayCola) 1.0f else 0.3f
            btnNext.isEnabled = hayCola

            val hayHistorial = musicaService!!.hasHistory()
            btnPrev.alpha = 1.0f
            btnPrev.isEnabled = true
            btnPrev.alpha = if (hayHistorial) 1.0f else 0.3f
            btnPrev.isEnabled = hayHistorial

        } else {
            // Oculta el reproductor si no hay canción.
            miniPlayerView!!.visibility = View.GONE
            animadorVinilo?.cancel()
        }
    }

    // Actualiza el adaptador del RecyclerView con la cola de reproducción actual.
    private fun actualizarListaCola() {
        if (musicaService == null) return
        val colaIds = musicaService!!.getCola()
        //Si esta la cola vacia la limpiamos
        if (colaIds.isEmpty()) {
            recyclerCola.adapter = null
            return
        }
        val db = AdminSQL(this)
        val todas = db.obtenerTodasLasCanciones()
        //Filtramos por id y las buscamos
        val cancionesCola = colaIds.mapNotNull { id ->
            val nombreRaw = resources.getResourceEntryName(id)
            todas.find { it.recursoRaw == nombreRaw }
        }
        recyclerCola.adapter = ColaAdapter(cancionesCola)
    }

    // Runnable para actualizar el progreso del SeekBar cada segundo.
    private val runnableSlider = object : Runnable {
        override fun run() {
            if (musicaService != null && isServiceBound) {
                //Si el telefono va lento y aun no inicio el miniplayer esto se ocupa de asegurar que se vea
                if (musicaService!!.getNombreCancion() != null && miniPlayerView?.visibility != View.VISIBLE) {
                    actualizarMiniPlayer()
                }
                //Si una cancion esta reproduciendose y el usuario no esta tocado la barrar
                if (musicaService!!.isPlaying() && !isUserSeeking) {
                    //La actualizamos
                    val actual = musicaService!!.getPosicionActual()
                    val total = musicaService!!.getDuracionTotal()
                    if (total > 0) {
                        if (miniSeekBar?.max != total) miniSeekBar?.max = total
                        miniSeekBar?.progress = actual
                    }
                }
            }
            //Delay de un segundo
            handlerUI.postDelayed(this, 1000)
        }
    }

    // Inicia el ciclo de actualización del SeekBar (borrar las anteriores para asegurar que funcione)
    private fun iniciarActualizacionSlider() {
        handlerUI.removeCallbacks(runnableSlider)
        handlerUI.post(runnableSlider)
    }

    // =========================================================
    //  TRANSICIONES Y POKEBALL
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Manejo personalizado le deciamos que no use el codigo default si no el nuestro
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            //Ahora ejecutara este codigo
            override fun handleOnBackPressed() {
                intentarSalir()
            }
        })
    }

    // Configura la interfaz del menú flotante y las transiciones iniciales.
    protected fun setupMarcoUi(tituloPantalla: String) {
        supportActionBar?.hide() //Guarda el ActionBar
        ocultarBarraDeEstado() //Barra o isla para tener la pantalla completa

        try {
            // Referencias a los elementos del layout de transición y menú.
            layoutTransicion = findViewById(R.id.layoutTransicion)
            cortinaVerde = findViewById(R.id.cortinaRoja)
            cortinaBlanca = findViewById(R.id.cortinaBlanca)
            btnFlotanteCentral = findViewById(R.id.btnMenuFlotante)
            fabOpcion1 = findViewById(R.id.fabOpcion1)
            fabOpcion2 = findViewById(R.id.fabOpcion2)
            fabOpcion3 = findViewById(R.id.fabOpcion3)
            tvTituloHeader = findViewById(R.id.tvTituloHeader)
            setupLogicaMenu()

            // Configura elevación para jerarquía visual.
            layoutTransicion.elevation = 100f
            btnFlotanteCentral.elevation = 110f

            //Preparemos el titulo para la animación
            if (tvTituloHeader != null) {
                tvTituloHeader!!.text = tituloPantalla
                tvTituloHeader!!.alpha = 0f
                tvTituloHeader!!.translationX = 300f
            }
            ajustarLimitesTransicion()

            // Inicia la animación de apertura una vez que la vista esté lista.
            layoutTransicion.post { animarApertura() }

        } catch (e: Exception) {
            Log.e("[DEBUG]", "Error en el setupMarcoUi$e")
        }
    }

    // Ejecuta la animación de entrada (cortinas abriéndose).
    private fun animarApertura() {
        layoutTransicion.visibility = View.VISIBLE
        //Espera a que se termine de dibujar la pantalla
        layoutTransicion.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                //Despues de la primera ejecucion desaparece
                layoutTransicion.viewTreeObserver.removeOnGlobalLayoutListener(this)
                //Calculamos donde esta el centro del telefono desde donde estaba el botón
                val distanciaBoton = getDistanciaCentroBoton()
                //Calculamos lo que necesitamos para llegar a ese lugar
                val magnitudApertura = abs(distanciaBoton)
                //Colocamos las cortinas tapando la pantalla
                cortinaVerde.translationY = 0f
                cortinaBlanca.translationY = 0f
                //Colocamos el boton en el centro
                btnFlotanteCentral.translationY = distanciaBoton

                //Animación botón flotante
                btnFlotanteCentral.animate()
                    .translationY(0f).rotation(0f).setDuration(600).setInterpolator(OvershootInterpolator(1.0f)).start()
                //Cortina verde sube
                cortinaVerde.animate()
                    .translationY(-magnitudApertura).setDuration(600).setInterpolator(AccelerateDecelerateInterpolator()).start()
                //Cortina blanca baja
                cortinaBlanca.animate()
                    .translationY(magnitudApertura).setDuration(600).setInterpolator(AccelerateDecelerateInterpolator())
                    .setListener(object : AnimatorListenerAdapter() {
                        //Terminamos la animacion destruimos las cortinas y iniciamos animación titulo
                        override fun onAnimationEnd(animation: Animator) {
                            layoutTransicion.visibility = View.GONE
                            animarEntradaTitulo()
                        }
                }).start()
            }
        })
    }

    // Calcula la distancia necesaria para animar el botón central desde el centro de la pantalla.
    private fun getDistanciaCentroBoton(): Float {
        //Cogemos la pantalla
        val parentView = btnFlotanteCentral.parent as View
        val locationParent = IntArray(2)
        //En que pixeles esta la pantalla
        parentView.getLocationOnScreen(locationParent)
        //Calcula en centro sumando la posición inicial + la mitad de la altura
        val parentCenterY = locationParent[1] + (parentView.height / 2f)

        //Ahora buscamos el boton
        val locationButton = IntArray(2)
        btnFlotanteCentral.getLocationOnScreen(locationButton)
        val buttonCenterY = locationButton[1] + (btnFlotanteCentral.height / 2f)
        //Ajustamos le boton (visualmente no se veia centrado para el ojo humano)
        val ajusteManualDp = -15f
        val ajustePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ajusteManualDp, resources.displayMetrics)

        //Destino Deseado (Centro Pantalla) - Posición Actual (Centro Botón) + ajuste
        return (parentCenterY - buttonCenterY) + ajustePx
    }

    // Configura los listeners de clic para el menú flotante y sus opciones.
    private fun setupLogicaMenu() {
        btnFlotanteCentral.setOnClickListener { animarMenuAbanico() }
        fabOpcion1.setOnClickListener { if (this !is CatalogoActivity) animarCierre(CatalogoActivity::class.java) }
        fabOpcion2.setOnClickListener { if (this !is MainActivity) animarCierre(MainActivity::class.java) }
        fabOpcion3.setOnClickListener { if (this !is RankingActivity) animarCierre(RankingActivity::class.java) }
    }

    // Anima la apertura y cierre del menú radial (abanico).
    private fun animarMenuAbanico() {
        //Cuando de lejos se van a abrir
        val radio = 85f * resources.displayMetrics.density
        //Donde están los botones ahora mismo
        val baseY = btnFlotanteCentral.translationY

        if (isMenuAbierto) {
            // Cierra el menú.
            btnFlotanteCentral.animate().rotation(0f).setDuration(300).start()

            cerrarFab(fabOpcion1)
            cerrarFab(fabOpcion2)
            cerrarFab(fabOpcion3)
        } else {
            // Abre el menú desplegando opciones.
            val interpolador = OvershootInterpolator(1.2f)//Mini rebote
            btnFlotanteCentral.animate().rotation(45f).setInterpolator(interpolador).setDuration(300).start()

            abrirFab(fabOpcion1, -radio * 0.85f, baseY - radio * 0.5f, -30f, interpolador)
            abrirFab(fabOpcion2, 0f, baseY - radio * 0.9f, 0f, interpolador)
            abrirFab(fabOpcion3, radio * 0.85f, baseY - radio * 0.5f, 30f, interpolador)
        }
        isMenuAbierto = !isMenuAbierto
    }

    // Helper para animar la aparición de un botón de opción.
    private fun abrirFab(fab: MaterialButton, x: Float, y: Float, rotacion: Float, interpolator: OvershootInterpolator) {
        fab.visibility = View.VISIBLE
        fab.alpha = 0f
        fab.scaleX = 0.5f; fab.scaleY = 0.5f
        fab.animate()
            .translationX(x).translationY(y)
            .rotation(rotacion)
            .alpha(1f)
            .scaleX(1f).scaleY(1f)
            .setDuration(350)
            .setInterpolator(interpolator)
            .start()
        fab.isClickable = true
    }

    // Helper para animar la desaparición de un botón de opción.
    private fun cerrarFab(fab: MaterialButton) {
        val baseY = btnFlotanteCentral.translationY
        fab.animate()
            .translationX(0f).translationY(baseY)
            .rotation(0f)
            .alpha(0f)
            .scaleX(0.5f).scaleY(0.5f)
            .setDuration(250)
            .withEndAction {
                fab.visibility = View.INVISIBLE
            }
            .start()
        fab.isClickable = false
    }

    // Maneja la navegación a otra actividad con una animación de transición .
    protected fun animarCierre(claseDestino: Class<*>) {
        //Cerramos el miniplayer si esta maximizado
        if (miniPlayerView != null) {
            val alturaOculta = 250f * resources.displayMetrics.density
            animarPlayerYBtnFlotante(alturaOculta, 0f)
            isColaVisible = false
        }

        layoutTransicion.visibility = View.VISIBLE
        //Aqui repetimos lo que hacia animaApertura pero al contrario
        btnFlotanteCentral.bringToFront()
        tvTituloHeader?.animate()?.alpha(0f)?.setDuration(200)?.start()
        //Cerramos abanico
        if (isMenuAbierto) animarMenuAbanico()

        val distanciaBoton = getDistanciaCentroBoton()
        val magnitudApertura = abs(distanciaBoton)
        cortinaVerde.translationY = -magnitudApertura
        cortinaBlanca.translationY = magnitudApertura

        // Anima el cierre de cortinas.
        btnFlotanteCentral.animate()
            .translationY(distanciaBoton)
            .rotation(360f)
            .setDuration(450)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()

        cortinaVerde.animate()
            .translationY(0f)
            .setDuration(450)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()

        cortinaBlanca.animate()
            .translationY(0f)
            .setDuration(450)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Efecto de "latido" antes de cambiar de actividad.
                    hacerPalpito {
                        val intent = Intent(this@MusicBaseActivity, claseDestino)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        overridePendingTransition(0, 0)
                        finish()
                    }
                }
        }).start()
    }

    // Animación de escala para el botón central.
    private fun hacerPalpito(onEnd: () -> Unit) {
        btnFlotanteCentral.animate()
            .scaleX(1.2f).scaleY(1.2f)
            .setDuration(150)
            .withEndAction {
                btnFlotanteCentral.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(150)
                    .withEndAction {
                        onEnd()
                    }.start()
        }.start()
    }

    // Anima la entrada del título de la pantalla.
    private fun animarEntradaTitulo() {
        tvTituloHeader?.animate()?.translationX(0f)?.alpha(1f)?.setDuration(400)?.setInterpolator(DecelerateInterpolator())?.start()
    }

    // Ajusta los márgenes del layout de transición para respetar áreas seguras (system bars).
    private fun ajustarLimitesTransicion() {
        //Margen de seguridad que asignamos previamente calculamos los pixeles reales
        val margenPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MARGEN_SEGURIDAD_DP, resources.displayMetrics).toInt()
        //Lo aplicamos
        layoutTransicion.setPadding(0, margenPx, 0, margenPx)
        //Cortamos todo lo que sobre salga
        layoutTransicion.clipToPadding = true
    }

    // Oculta la barra de estado y navegación para una experiencia inmersiva.
    private fun ocultarBarraDeEstado() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Exception)
        {
            Log.e("[DEBUG]", "Error en el ocultarBarraDeEstado$e")
        }
    }

    // Maneja el comportamiento al presionar "Atrás".
    protected fun intentarSalir() {
        //Pregunta soy la pantalla principal? DetalleActivity no lo es entonces vuelve atrás
        if (this is MainActivity) {
            //Equivale a pulsar el boton home
            moveTaskToBack(true)
        }
        else finish() //destruimos la pestaña
    }

    // Lifecycle: Se ejecuta al iniciar la actividad. Registra el receiver y conecta el servicio.
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("EVENTO_ACTUALIZAR_MINIPLAYER")
        ContextCompat.registerReceiver(
            this,
            musicReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        Intent(this, MusicaService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        iniciarActualizacionSlider()
    }

    // Lifecycle: Se ejecuta al detener la actividad. Desregistra el receiver y desconecta el servicio.
    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(musicReceiver) } catch (e: Exception)
        {
            Log.e("[DEBUG]", "Error en el onStop$e")
        }

        if (isServiceBound) {
            unbindService(connection)
            isServiceBound = false
        }
        handlerUI.removeCallbacks(runnableSlider)
    }
}