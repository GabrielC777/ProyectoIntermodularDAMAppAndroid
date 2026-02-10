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
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlin.math.abs

open class MusicBaseActivity : AppCompatActivity() {

    // =========================================================
    //  VARIABLES GLOBALES
    // =========================================================
    protected var musicaService: MusicaService? = null
    protected var isServiceBound = false

    // UI MiniPlayer
    private var miniPlayerView: View? = null
    private var miniSeekBar: SeekBar? = null
    private val handlerUI = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    //Variables cola de reproducción
    private var isColaVisible = false
    private lateinit var recyclerCola: RecyclerView

    // UI Transiciones , menu y animaciones
    private var animadorVinilo: ObjectAnimator? = null
    private lateinit var layoutTransicion: ConstraintLayout
    private lateinit var cortinaRoja: View
    private lateinit var cortinaBlanca: View
    protected lateinit var btnPokeballCentral: FrameLayout
    protected lateinit var fabOpcion1: MaterialButton
    protected lateinit var fabOpcion2: MaterialButton
    protected lateinit var fabOpcion3: MaterialButton
    protected var isMenuAbierto = false
    private var tvTituloHeader: TextView? = null
    private val MARGEN_SEGURIDAD_DP = 60f

    // =========================================================
    //  CONEXIÓN CON SERVICIO DE MÚSICA
    // =========================================================
    private val musicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "EVENTO_ACTUALIZAR_MINIPLAYER") {
                actualizarMiniPlayer()
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicaService.LocalBinder
            musicaService = binder.getService()
            isServiceBound = true
            onMusicaServiceConnected()

            // Inicializar UI Música
            setupMiniPlayer()
            actualizarMiniPlayer()
            iniciarActualizacionSlider()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
            musicaService = null
        }
    }

    protected open fun onMusicaServiceConnected() {}

    fun reproducirCancionDirecta(idCancion: Int) {
        val intent = Intent(this, MusicaService::class.java)
        intent.action = "CAMBIAR_CANCION"
        intent.putExtra("ID_CANCION", idCancion)
        startService(intent)
        handlerUI.postDelayed({ actualizarMiniPlayer() }, 50)
    }

    // =========================================================
    //  LÓGICA MINI PLAYER (RODILLO)
    // =========================================================
    protected fun setupMiniPlayer() {
        // -----------------------------------------------------------
        // 1. OBTENCIÓN DE REFERENCIAS Y VALIDACIÓN
        // -----------------------------------------------------------
        miniPlayerView = findViewById(R.id.layoutMiniPlayer)
        if (miniPlayerView == null) return

        // Elevación alta para asegurar que se dibuje por encima de otros elementos
        miniPlayerView!!.elevation = 105f

        // Referencias a las vistas internas del MiniPlayer
        val cabecera = miniPlayerView!!.findViewById<View>(R.id.cabeceraPlayer)
        val btnVerCola = miniPlayerView!!.findViewById<ImageButton>(R.id.btnVerCola)
        val btnPlay = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniPlay)
        val btnNext = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniNext) // Nuevo: Siguiente
        val btnPrev = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniPrev) // Nuevo: Anterior
        miniSeekBar = miniPlayerView!!.findViewById(R.id.miniSeekBar)
        recyclerCola = miniPlayerView!!.findViewById(R.id.recyclerCola)

        // Configuración del RecyclerView de la cola
        if (recyclerCola != null) {
            recyclerCola.layoutManager = LinearLayoutManager(this)
        }

        // -----------------------------------------------------------
        // 2. CÁLCULO DE DIMENSIONES Y ESTADO INICIAL
        // -----------------------------------------------------------
        // Definimos cuánto debe bajar el player para ocultarse (Altura total - Altura cabecera)
        // 250dp convertido a píxeles
        val alturaDesplazamiento = 250f * resources.displayMetrics.density

        // Posición inicial: Abajo (Cerrado)
        miniPlayerView!!.translationY = alturaDesplazamiento
        moverMenuCompleto(0f)
        isColaVisible = false

        // -----------------------------------------------------------
        // 3. LÓGICA DE ARRASTRE (SWIPE) CONTROLADA
        // -----------------------------------------------------------
        cabecera.setOnTouchListener(object : View.OnTouchListener {
            var startRawY = 0f    // Posición absoluta del dedo al tocar
            var startViewY = 0f   // Posición de la vista al tocar
            var lastDiff = 0f     // Para medir velocidad/dirección

            override fun onTouch(v: View?, event: android.view.MotionEvent?): Boolean {
                when (event?.action) {
                    // A) EL DEDO TOCA LA PANTALLA
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startRawY = event.rawY
                        startViewY = miniPlayerView!!.translationY
                        // Si el menú flotante (Pokeball) estaba abierto, lo cerramos por seguridad
                        if (isMenuAbierto) cerrarMenuAbanicoRapido()
                        return true // Devolvemos true para indicar que estamos manejando el evento
                    }

                    // B) EL DEDO SE MUEVE (ARRASTRE)
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - startRawY // Diferencia desde el punto inicial
                        var newY = startViewY + dy      // Nueva posición calculada
                        lastDiff = dy                   // Guardamos la dirección para usarla en ACTION_UP

                        // Restricciones (Clamping):
                        // 0f = Totalmente abierto (Arriba)
                        // alturaDesplazamiento = Totalmente cerrado (Abajo)
                        if (newY < 0f) newY = 0f
                        if (newY > alturaDesplazamiento) newY = alturaDesplazamiento

                        // Aplicamos el movimiento al MiniPlayer
                        miniPlayerView!!.translationY = newY

                        // Efecto Parallax: Movemos el menú principal en sentido contrario o coordinado
                        val offsetMenu = newY - alturaDesplazamiento
                        moverMenuCompleto(offsetMenu)
                        return true
                    }

                    // C) EL DEDO SE LEVANTA (DECISIÓN FINAL)
                    android.view.MotionEvent.ACTION_UP -> {
                        val currentY = miniPlayerView!!.translationY
                        val umbral = alturaDesplazamiento / 2
                        var abrir = false

                        // Lógica de decisión:
                        // 1. Si hubo un gesto rápido (swipe) hacia arriba (>10px de inercia)
                        if (kotlin.math.abs(lastDiff) > 10) {
                            if (lastDiff < 0) abrir = true // Dirección negativa es hacia arriba
                        }
                        // 2. Si fue lento, miramos si pasó la mitad de la pantalla
                        else {
                            if (currentY < umbral) abrir = true
                        }

                        // Ejecutar animación final
                        if (abrir) {
                            // ABRIR: Player sube a 0, Menú baja
                            animarTodo(0f, -alturaDesplazamiento)
                            isColaVisible = true
                            actualizarListaCola()
                            btnVerCola?.animate()?.rotation(0f)?.start() // Flecha apunta arriba
                        } else {
                            // CERRAR: Player baja, Menú sube
                            animarTodo(alturaDesplazamiento, 0f)
                            isColaVisible = false
                            btnVerCola?.animate()?.rotation(180f)?.start() // Flecha apunta abajo
                        }

                        // Limpieza
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

        // Botón Play/Pause
        btnPlay?.setOnClickListener {
            if (musicaService != null) {
                val action = if (musicaService!!.isPlaying()) "PAUSE" else "RESUME"
                val intent = Intent(this, MusicaService::class.java).apply { this.action = action }
                startService(intent)
            }
        }

        // Botón Siguiente (Nuevo)
        btnNext?.setOnClickListener {
            if (musicaService != null) {
                startService(Intent(this, MusicaService::class.java).apply { action = "ACTION_NEXT" })
            }
        }

        // Botón Anterior (Nuevo)
        btnPrev?.setOnClickListener {
            if (musicaService != null) {
                startService(Intent(this, MusicaService::class.java).apply { action = "ACTION_PREV" })
            }
        }

        // Botón para ver/ocultar la cola manualmente
        btnVerCola?.setOnClickListener {
            if (isColaVisible) {
                animarTodo(alturaDesplazamiento, 0f)
                btnVerCola.animate().rotation(180f).start()
            } else {
                animarTodo(0f, -alturaDesplazamiento)
                btnVerCola.animate().rotation(0f).start()
                actualizarListaCola()
            }
            isColaVisible = !isColaVisible
        }

        // Configuración básica del SeekBar (barra de progreso)
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

        // Importante: Permitir clicks en el player para que no traspasen al fondo
        miniPlayerView!!.isClickable = true
        miniPlayerView!!.setOnClickListener {
            // Consumir el evento de click normal
        }
    }

    // --- HELPER 1: MOVER TODO FÍSICAMENTE ---
    private fun moverMenuCompleto(offset: Float) {
        // Todas las llamadas ahora verifican isInitialized internamente, así que es seguro llamar a esto
        if (::btnPokeballCentral.isInitialized) btnPokeballCentral.translationY = offset
        if (::fabOpcion1.isInitialized) fabOpcion1.translationY = offset
        if (::fabOpcion2.isInitialized) fabOpcion2.translationY = offset
        if (::fabOpcion3.isInitialized) fabOpcion3.translationY = offset
    }

    // --- HELPER 2: ANIMAR TODO ---
    private fun animarTodo(destinoPlayer: Float, destinoMenu: Float) {
        miniPlayerView!!.animate()
            .translationY(destinoPlayer)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()

        if (::btnPokeballCentral.isInitialized) {
            btnPokeballCentral.animate()
                .translationY(destinoMenu)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()

            val views = listOf(fabOpcion1, fabOpcion2, fabOpcion3)
            views.forEach {
                if (it != null && ::fabOpcion1.isInitialized) {
                    it.animate().translationY(destinoMenu).setDuration(300).setInterpolator(OvershootInterpolator(0.8f)).start()
                }
            }
        }
    }

    private fun cerrarMenuAbanicoRapido() {
        if (!::btnPokeballCentral.isInitialized) return

        isMenuAbierto = false
        btnPokeballCentral.animate().rotation(0f).setDuration(200).start()
        val currentY = btnPokeballCentral.translationY

        val views = listOf(fabOpcion1, fabOpcion2, fabOpcion3)
        views.forEach {
            if (it != null && ::fabOpcion1.isInitialized) {
                it.visibility = View.INVISIBLE
                it.translationX = 0f
                it.translationY = currentY
            }
        }
    }

    protected open fun actualizarMiniPlayer() {
        if (miniPlayerView == null || musicaService == null) return

        val nombreRaw = musicaService!!.getNombreCancion()
        val isPlaying = musicaService!!.isPlaying()

        // Referencias UI
        val tvTitulo = miniPlayerView!!.findViewById<TextView>(R.id.tvMiniTitulo)
        val tvArtista = miniPlayerView!!.findViewById<TextView>(R.id.tvMiniArtista)
        val ivImagen = miniPlayerView!!.findViewById<ImageView>(R.id.ivMiniImagen)
        val btnPlay = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniPlay)
        val btnNext = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniNext)
        val btnPrev = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniPrev)

        // Animación vinilo (se mantiene igual)
        if (animadorVinilo == null && ivImagen != null) {
            animadorVinilo = ObjectAnimator.ofFloat(ivImagen, "rotation", 0f, 360f).apply {
                duration = 4000
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
        }

        if (nombreRaw != null) {
            // Mostrar Player si estaba oculto
            if (miniPlayerView!!.visibility != View.VISIBLE) {
                miniPlayerView!!.visibility = View.VISIBLE
            }
            if (isColaVisible) actualizarListaCola()

            // Actualizar SeekBar
            val duracion = musicaService!!.getDuracionTotal()
            if (duracion > 0) miniSeekBar?.max = duracion

            // Obtener info de BD
            val db = AdminSQL(this)
            val cancion = db.obtenerTodasLasCanciones().find { it.recursoRaw == nombreRaw }

            if (cancion != null) {
                tvTitulo.text = cancion.titulo
                tvTitulo.isSelected = true // Para marquee
                tvArtista.text = cancion.artista
                val resId = resources.getIdentifier(cancion.imagenUri, "drawable", packageName)
                if (resId != 0) ivImagen.setImageResource(resId)
            }


            // Estado Botón Play/Pause
            if (isPlaying) {
                btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                iniciarActualizacionSlider()
                if (animadorVinilo?.isPaused == true) animadorVinilo?.resume()
                else if (animadorVinilo?.isRunning == false) animadorVinilo?.start()
            } else {
                btnPlay.setImageResource(R.drawable.boton_de_play)
                if (animadorVinilo?.isRunning == true) animadorVinilo?.pause()
            }

            // LOGICA VISUAL BOTONES NEXT / PREV
            val hayCola = musicaService!!.getCola().isNotEmpty()
            btnNext.alpha = if (hayCola) 1.0f else 0.3f
            btnNext.isEnabled = hayCola

            val hayHistorial = musicaService!!.hasHistory()
            // Permitimos siempre Prev para reiniciar canción (como Spotify), o solo si hay historial
            // Si quieres reiniciar canción actual siempre:
            btnPrev.alpha = 1.0f
            btnPrev.isEnabled = true
            // Si solo quieres ir atrás si hay historial:
            btnPrev.alpha = if (hayHistorial) 1.0f else 0.3f
            btnPrev.isEnabled = hayHistorial

        } else {
            miniPlayerView!!.visibility = View.GONE
            animadorVinilo?.cancel()
        }
    }

    private fun actualizarListaCola() {
        if (musicaService == null) return
        val colaIds = musicaService!!.getCola()
        if (colaIds.isEmpty()) {
            recyclerCola.adapter = null
            return
        }
        val db = AdminSQL(this)
        val todas = db.obtenerTodasLasCanciones()
        val cancionesCola = colaIds.mapNotNull { id ->
            val nombreRaw = resources.getResourceEntryName(id)
            todas.find { it.recursoRaw == nombreRaw }
        }
        recyclerCola.adapter = ColaAdapter(cancionesCola)
    }

    private val runnableSlider = object : Runnable {
        override fun run() {
            if (musicaService != null && isServiceBound) {
                if (musicaService!!.getNombreCancion() != null && miniPlayerView?.visibility != View.VISIBLE) {
                    actualizarMiniPlayer()
                }
                if (musicaService!!.isPlaying() && !isUserSeeking) {
                    val actual = musicaService!!.getPosicionActual()
                    val total = musicaService!!.getDuracionTotal()
                    if (total > 0) {
                        if (miniSeekBar?.max != total) miniSeekBar?.max = total
                        miniSeekBar?.progress = actual
                    }
                }
            }
            handlerUI.postDelayed(this, 1000)
        }
    }

    private fun iniciarActualizacionSlider() {
        handlerUI.removeCallbacks(runnableSlider)
        handlerUI.post(runnableSlider)
    }

    // =========================================================
    //  TRANSICIONES Y POKEBALL
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                intentarSalir()
            }
        })
    }

    protected fun setupPokeballUi(tituloPantalla: String) {
        supportActionBar?.hide()
        ocultarBarraDeEstado()

        try {
            layoutTransicion = findViewById(R.id.layoutTransicion)
            cortinaRoja = findViewById(R.id.cortinaRoja)
            cortinaBlanca = findViewById(R.id.cortinaBlanca)
            btnPokeballCentral = findViewById(R.id.btnMenuFlotante)
            fabOpcion1 = findViewById(R.id.fabOpcion1)
            fabOpcion2 = findViewById(R.id.fabOpcion2)
            fabOpcion3 = findViewById(R.id.fabOpcion3)
            tvTituloHeader = findViewById(R.id.tvTituloHeader)
            setupLogicaMenu()

            // AJUSTE CLAVE 2: JERARQUÍA VISUAL PARA EVITAR GLITCH
            layoutTransicion.elevation = 100f
            btnPokeballCentral.elevation = 110f

            if (tvTituloHeader != null) {
                tvTituloHeader!!.text = tituloPantalla
                tvTituloHeader!!.alpha = 0f
                tvTituloHeader!!.translationX = 300f
            }
            ajustarLimitesTransicion()
            layoutTransicion.post { animarApertura() }
        } catch (e: Exception) { }
    }

    private fun animarApertura() {
        layoutTransicion.visibility = View.VISIBLE
        layoutTransicion.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                layoutTransicion.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val distanciaBoton = getDistanciaCentroBoton()
                val magnitudApertura = abs(distanciaBoton)
                cortinaRoja.translationY = 0f
                cortinaBlanca.translationY = 0f
                btnPokeballCentral.translationY = distanciaBoton
                btnPokeballCentral.animate().translationY(0f).rotation(0f).setDuration(600).setInterpolator(OvershootInterpolator(1.0f)).start()
                cortinaRoja.animate().translationY(-magnitudApertura).setDuration(600).setInterpolator(AccelerateDecelerateInterpolator()).start()
                cortinaBlanca.animate().translationY(magnitudApertura).setDuration(600).setInterpolator(AccelerateDecelerateInterpolator()).setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        layoutTransicion.visibility = View.GONE
                        animarEntradaTitulo()
                    }
                }).start()
            }
        })
    }

    private fun getDistanciaCentroBoton(): Float {
        val parentView = btnPokeballCentral.parent as View
        val locationParent = IntArray(2)
        parentView.getLocationOnScreen(locationParent)
        val parentCenterY = locationParent[1] + (parentView.height / 2f)
        val locationButton = IntArray(2)
        btnPokeballCentral.getLocationOnScreen(locationButton)
        val buttonCenterY = locationButton[1] + (btnPokeballCentral.height / 2f)
        val ajusteManualDp = -15f
        val ajustePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ajusteManualDp, resources.displayMetrics)
        return (parentCenterY - buttonCenterY) + ajustePx
    }

    private fun setupLogicaMenu() {
        btnPokeballCentral.setOnClickListener { animarMenuAbanico() }
        fabOpcion1.setOnClickListener { if (this !is CatalogoActivity) navegarConAnimacion(CatalogoActivity::class.java) }
        fabOpcion2.setOnClickListener { if (this !is MainActivity) navegarConAnimacion(MainActivity::class.java) }
        fabOpcion3.setOnClickListener { if (this !is RankingActivity) navegarConAnimacion(RankingActivity::class.java) }
    }

    private fun animarMenuAbanico() {
        val radio = 85f * resources.displayMetrics.density
        val baseY = btnPokeballCentral.translationY

        if (isMenuAbierto) {
            btnPokeballCentral.animate().rotation(0f).setDuration(300).start()
            cerrarFab(fabOpcion1)
            cerrarFab(fabOpcion2)
            cerrarFab(fabOpcion3)
        } else {
            val interpolador = OvershootInterpolator(1.2f)
            btnPokeballCentral.animate().rotation(45f).setInterpolator(interpolador).setDuration(300).start()
            abrirFab(fabOpcion1, -radio * 0.85f, baseY - radio * 0.5f, -30f, interpolador)
            abrirFab(fabOpcion2, 0f, baseY - radio * 0.9f, 0f, interpolador)
            abrirFab(fabOpcion3, radio * 0.85f, baseY - radio * 0.5f, 30f, interpolador)
        }
        isMenuAbierto = !isMenuAbierto
    }

    private fun abrirFab(fab: MaterialButton, x: Float, y: Float, rotacion: Float, interpolator: OvershootInterpolator) {
        fab.visibility = View.VISIBLE
        fab.alpha = 0f
        fab.scaleX = 0.5f; fab.scaleY = 0.5f
        fab.animate().translationX(x).translationY(y).rotation(rotacion).alpha(1f).scaleX(1f).scaleY(1f).setDuration(350).setInterpolator(interpolator).start()
        fab.isClickable = true
    }

    private fun cerrarFab(fab: MaterialButton) {
        val baseY = btnPokeballCentral.translationY
        fab.animate().translationX(0f).translationY(baseY).rotation(0f).alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(250).withEndAction { fab.visibility = View.INVISIBLE }.start()
        fab.isClickable = false
    }

    protected fun navegarConAnimacion(claseDestino: Class<*>) {
        if (miniPlayerView != null) {
            val alturaOculta = 250f * resources.displayMetrics.density
            animarTodo(alturaOculta, 0f)
            isColaVisible = false
        }

        layoutTransicion.visibility = View.VISIBLE
        btnPokeballCentral.bringToFront()
        tvTituloHeader?.animate()?.alpha(0f)?.setDuration(200)?.start()
        if (isMenuAbierto) animarMenuAbanico()

        val distanciaBoton = getDistanciaCentroBoton()
        val magnitudApertura = abs(distanciaBoton)
        cortinaRoja.translationY = -magnitudApertura
        cortinaBlanca.translationY = magnitudApertura

        btnPokeballCentral.animate().translationY(distanciaBoton).rotation(360f).setDuration(450).setInterpolator(AccelerateDecelerateInterpolator()).start()
        cortinaRoja.animate().translationY(0f).setDuration(450).setInterpolator(AccelerateDecelerateInterpolator()).start()
        cortinaBlanca.animate().translationY(0f).setDuration(450).setInterpolator(AccelerateDecelerateInterpolator()).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
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

    private fun hacerPalpito(onEnd: () -> Unit) {
        btnPokeballCentral.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).withEndAction {
            btnPokeballCentral.animate().scaleX(1f).scaleY(1f).setDuration(150).withEndAction { onEnd() }.start()
        }.start()
    }

    private fun animarEntradaTitulo() {
        tvTituloHeader?.animate()?.translationX(0f)?.alpha(1f)?.setDuration(400)?.setInterpolator(DecelerateInterpolator())?.start()
    }

    private fun ajustarLimitesTransicion() {
        val margenPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MARGEN_SEGURIDAD_DP, resources.displayMetrics).toInt()
        layoutTransicion.setPadding(0, margenPx, 0, margenPx)
        layoutTransicion.clipToPadding = true
    }

    private fun ocultarBarraDeEstado() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Exception) { }
    }

    protected fun intentarSalir() {
        if (this is MainActivity) moveTaskToBack(true) else finish()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("EVENTO_ACTUALIZAR_MINIPLAYER")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(musicReceiver, filter)
        }

        Intent(this, MusicaService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        iniciarActualizacionSlider()
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(musicReceiver) } catch (e: Exception) {}
        if (isServiceBound) {
            unbindService(connection)
            isServiceBound = false
        }
        handlerUI.removeCallbacks(runnableSlider)
    }
}