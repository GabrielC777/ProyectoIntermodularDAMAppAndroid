package com.example.musicsearch

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton // IMPORTADO
import android.widget.ImageView   // IMPORTADO
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import kotlin.math.abs

open class MusicBaseActivity : AppCompatActivity() {
    protected var musicaService: MusicaService? = null
    protected var isServiceBound = false

    // VARIABLES DEL MINIPLAYER Y SLIDER
    private var miniPlayerView: View? = null
    private var miniSeekBar: SeekBar? = null
    private val handlerUI = Handler(Looper.getMainLooper())
    private var isUserSeeking = false // Para saber si el usuario está arrastrando la barra

    // RECEPTOR DE AVISOS (Para actualizar al instante)
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

            // Inicializar UI
            setupMiniPlayer()
            actualizarMiniPlayer()
            iniciarActualizacionSlider() // Arrancar el reloj del slider

            // Truco: Forzamos otra actualización 100ms después por si la UI no estaba lista
            handlerUI.postDelayed({ actualizarMiniPlayer() }, 100)
            handlerUI.postDelayed({ actualizarMiniPlayer() }, 500)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
            musicaService = null
        }
    }

    // Método vacío para que DetalleActivity lo sobrescriba
    protected open fun onMusicaServiceConnected() {}

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

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
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

        layoutTransicion = findViewById(R.id.layoutTransicion)
        cortinaRoja = findViewById(R.id.cortinaRoja)
        cortinaBlanca = findViewById(R.id.cortinaBlanca)

        try {
            btnPokeballCentral = findViewById(R.id.btnMenuFlotante)
            fabOpcion1 = findViewById(R.id.fabOpcion1)
            fabOpcion2 = findViewById(R.id.fabOpcion2)
            fabOpcion3 = findViewById(R.id.fabOpcion3)
            setupLogicaMenu()
        } catch (e: Exception) { }

        tvTituloHeader = findViewById(R.id.tvTituloHeader)

        if (tvTituloHeader != null) {
            tvTituloHeader!!.text = tituloPantalla
            tvTituloHeader!!.alpha = 0f
            tvTituloHeader!!.translationX = 300f
        }

        ajustarLimitesTransicion()
        animarApertura()
    }

    // =========================================================================================
    //  SECCIÓN 1: LÓGICA DEL MENÚ (ORIGINAL)
    // =========================================================================================

    private fun setupLogicaMenu() {
        btnPokeballCentral.setOnClickListener { animarMenuAbanico() }
        fabOpcion1.setOnClickListener { accionBoton1() }
        fabOpcion2.setOnClickListener { accionBoton2() }
        fabOpcion3.setOnClickListener { accionBoton3() }
    }

    private fun animarMenuAbanico() {
        val radio = 85f * resources.displayMetrics.density
        if (isMenuAbierto) {
            btnPokeballCentral.animate().rotation(0f).setDuration(300).start()
            cerrarFab(fabOpcion1)
            cerrarFab(fabOpcion2)
            cerrarFab(fabOpcion3)
        } else {
            val interpolador = OvershootInterpolator(1.2f)
            btnPokeballCentral.animate().rotation(45f).setInterpolator(interpolador).setDuration(300).start()
            abrirFab(fabOpcion1, -radio * 0.85f, -radio * 0.5f, -30f, interpolador)
            abrirFab(fabOpcion2, 0f, -radio * 0.9f, 0f, interpolador)
            abrirFab(fabOpcion3, radio * 0.85f, -radio * 0.5f, 30f, interpolador)
        }
        isMenuAbierto = !isMenuAbierto
    }

    private fun abrirFab(fab: MaterialButton, x: Float, y: Float, rotacionFinal: Float, interpolator: OvershootInterpolator) {
        fab.visibility = View.VISIBLE
        fab.alpha = 0f
        fab.scaleX = 0.5f
        fab.scaleY = 0.5f
        fab.animate().translationX(x).translationY(y).rotation(rotacionFinal).alpha(1f).scaleX(1f).scaleY(1f).setDuration(350).setInterpolator(interpolator).start()
        fab.isClickable = true
    }

    private fun cerrarFab(fab: MaterialButton) {
        fab.animate().translationX(0f).translationY(0f).rotation(0f).alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(250).withEndAction { fab.visibility = View.INVISIBLE }.start()
        fab.isClickable = false
    }

    open fun accionBoton1() { if (this !is CatalogoActivity) navegarConAnimacion(CatalogoActivity::class.java) }
    open fun accionBoton2() { if (this !is MainActivity) navegarConAnimacion(MainActivity::class.java) }
    open fun accionBoton3() { if (this !is RankingActivity) navegarConAnimacion(RankingActivity::class.java) }

    protected fun intentarSalir() {
        if (this is MainActivity) moveTaskToBack(true) else finish()
    }

    // =========================================================================================
    //  SECCIÓN 2: TRANSICIONES DE PANTALLA (ORIGINAL - NO TOCADA)
    // =========================================================================================

    protected fun navegarConAnimacion(claseDestino: Class<*>) {
        layoutTransicion.visibility = View.VISIBLE
        btnPokeballCentral.bringToFront()
        tvTituloHeader?.animate()?.alpha(0f)?.setDuration(200)?.start()
        if (isMenuAbierto) animarMenuAbanico()

        val distanciaBoton = getDistanciaCentroBoton()
        val magnitudApertura = abs(distanciaBoton)

        cortinaRoja.translationY = -magnitudApertura
        cortinaBlanca.translationY = magnitudApertura

        // Subida: Solo sube y gira una vez para colocarse, luego se queda quieto
        btnPokeballCentral.animate()
            .translationY(distanciaBoton)
            .rotation(360f) // Gira una vez al subir
            .setDuration(450)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        cortinaRoja.animate().translationY(0f).setDuration(450).setInterpolator(AccelerateDecelerateInterpolator()).start()
        cortinaBlanca.animate().translationY(0f).setDuration(450).setInterpolator(AccelerateDecelerateInterpolator()).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // Hacemos el efecto de pálpito (grande-pequeño) solo una vez al terminar
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

                // Bajada: Gira una vez al revés mientras baja
                btnPokeballCentral.animate()
                    .translationY(0f)
                    .rotation(0f) // Vuelve a su posición original
                    .setDuration(600)
                    .setInterpolator(OvershootInterpolator(1.0f))
                    .start()

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

    private fun animarEntradaTitulo() {
        tvTituloHeader?.animate()?.translationX(0f)?.alpha(1f)?.setDuration(400)?.setInterpolator(DecelerateInterpolator())?.start()
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

    // Efecto de latido único al terminar la subida
    private fun hacerPalpito(onEnd: () -> Unit) {
        btnPokeballCentral.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).withEndAction {
            btnPokeballCentral.animate().scaleX(1f).scaleY(1f).setDuration(150).withEndAction { onEnd() }.start()
        }.start()
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
        } catch (e: Exception) { e.printStackTrace() }
    }

    // =========================================================================================
    //  SECCIÓN 3: LÓGICA DEL MINI PLAYER (AÑADIDO SIN TOCAR LO ANTERIOR)
    // =========================================================================================

    protected fun setupMiniPlayer() {
        miniPlayerView = findViewById(R.id.layoutMiniPlayer)

        if (miniPlayerView != null) {
            // --- FIX VISUAL: FUERZA BRUTA PARA QUE NO LO TAPE LA TRANSICIÓN ---
            miniPlayerView!!.translationZ = 1000f // Z-Index superior a cualquier elevación
            miniPlayerView!!.bringToFront()       // Mover al final de la lista de dibujado
            miniPlayerView!!.requestLayout()      // Forzar repintado
            val parent = miniPlayerView!!.parent as? android.view.ViewGroup
            parent?.clipChildren = false          // Permitir que flote libremente
            parent?.clipToPadding = false
            // ------------------------------------------------------------------

            val btnPlay = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniPlay)
            val miniSeekBar = miniPlayerView!!.findViewById<SeekBar>(R.id.miniSeekBar)

            // 1. Botón Play/Pause
            btnPlay.setOnClickListener {
                if (musicaService != null) {
                    if (musicaService!!.isPlaying()) {
                        val intent = Intent(this, MusicaService::class.java)
                        intent.action = "PAUSE"
                        startService(intent)
                    } else {
                        val intent = Intent(this, MusicaService::class.java)
                        intent.action = "RESUME"
                        startService(intent)
                    }
                    // La actualización visual vendrá sola por el BroadcastReceiver
                }
            }

            // 2. Control del Slider (SeekBar)
            miniSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    // Opcional: Actualizar texto de tiempo si lo tuvieras
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isUserSeeking = true // El usuario agarró la bolita
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isUserSeeking = false // El usuario soltó la bolita
                    if (musicaService != null && seekBar != null) {
                        musicaService!!.seekTo(seekBar.progress) // Mover canción
                    }
                }
            })

            // 3. Click en el cuerpo -> Detalles
            miniPlayerView!!.setOnClickListener {
                if (musicaService != null && musicaService!!.getNombreCancion() != null) {
                    val nombreRaw = musicaService!!.getNombreCancion()
                    val db = AdminSQL(this)
                    val cancion = db.obtenerTodasLasCanciones().find { it.recursoRaw == nombreRaw }
                    if (cancion != null) {
                        val intent = Intent(this, DetalleActivity::class.java)
                        intent.putExtra("ID_CANCION", cancion.id)
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                }
            }
        }
    }

    protected fun actualizarMiniPlayer() {
        if (miniPlayerView == null || musicaService == null) return

        val nombreRaw = musicaService!!.getNombreCancion()
        val isPlaying = musicaService!!.isPlaying()

        val tvTitulo = miniPlayerView!!.findViewById<TextView>(R.id.tvMiniTitulo)
        val tvArtista = miniPlayerView!!.findViewById<TextView>(R.id.tvMiniArtista)
        val ivImagen = miniPlayerView!!.findViewById<ImageView>(R.id.ivMiniImagen)
        val btnPlay = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniPlay)

        if (nombreRaw != null) {
            miniPlayerView!!.visibility = View.VISIBLE

            // Configurar SeekBar con la duración total
            val duracion = musicaService!!.getDuracionTotal()
            miniSeekBar?.max = duracion

            val db = AdminSQL(this)
            val cancion = db.obtenerTodasLasCanciones().find { it.recursoRaw == nombreRaw }

            if (cancion != null) {
                tvTitulo.text = cancion.titulo
                tvTitulo.isSelected = true
                tvArtista.text = cancion.artista
                val resId = resources.getIdentifier(cancion.imagenUri, "drawable", packageName)
                if (resId != 0) ivImagen.setImageResource(resId)
            }

            if (isPlaying) {
                btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                iniciarActualizacionSlider() // Asegurar que el reloj corre
            } else {
                btnPlay.setImageResource(R.drawable.boton_de_play)
            }
        } else {
            miniPlayerView!!.visibility = View.GONE
        }
    }

    // --- RELOJ DEL SLIDER ---
    private val runnableSlider = object : Runnable {
        override fun run() {
            if (musicaService != null && isServiceBound && !isUserSeeking) {
                if (musicaService!!.isPlaying()) {
                    val actual = musicaService!!.getPosicionActual()
                    miniSeekBar?.progress = actual
                }
            }
            handlerUI.postDelayed(this, 1000) // Repetir cada segundo
        }
    }

    private fun iniciarActualizacionSlider() {
        detenerActualizacionSlider() // Limpiar anteriores para no duplicar
        handlerUI.post(runnableSlider)
    }

    private fun detenerActualizacionSlider() {
        handlerUI.removeCallbacks(runnableSlider)
    }

    override fun onResume() {
        super.onResume()
        // Registrar el receptor de avisos
        val filter = IntentFilter("EVENTO_ACTUALIZAR_MINIPLAYER")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(musicReceiver, filter)
        }
        actualizarMiniPlayer()
        iniciarActualizacionSlider()
    }

    override fun onPause() {
        super.onPause()
        // Dejar de escuchar avisos y parar reloj para ahorrar batería
        try { unregisterReceiver(musicReceiver) } catch (e: Exception) {}
        detenerActualizacionSlider()
    }

    override fun onStart() {
        super.onStart()
        Intent(this, MusicaService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isServiceBound) {
            unbindService(connection)
            isServiceBound = false
        }
    }
}