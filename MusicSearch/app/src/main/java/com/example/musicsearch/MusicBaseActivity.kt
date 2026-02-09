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
    //  LÓGICA MINI PLAYER (Slider y Botones)
    // =========================================================
    protected fun setupMiniPlayer() {
        miniPlayerView = findViewById(R.id.layoutMiniPlayer)

        if (miniPlayerView != null) {
            val btnPlay = miniPlayerView!!.findViewById<ImageButton>(R.id.btnMiniPlay)
            miniSeekBar = miniPlayerView!!.findViewById(R.id.miniSeekBar)

            btnPlay.setOnClickListener {
                if (musicaService != null) {
                    val action = if (musicaService!!.isPlaying()) "PAUSE" else "RESUME"
                    val intent = Intent(this, MusicaService::class.java).apply { this.action = action }
                    startService(intent)
                    btnPlay.setImageResource(if (action == "PAUSE") R.drawable.boton_de_play else android.R.drawable.ic_media_pause)
                }
            }

            miniSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(seekBar: SeekBar?) { isUserSeeking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isUserSeeking = false
                    if (musicaService != null && seekBar != null) {
                        val total = musicaService!!.getDuracionTotal()
                        if (total > 0) {
                            if (seekBar.max != total) seekBar.max = total
                            musicaService!!.seekTo(seekBar.progress)
                        }
                    }
                }
            })

            miniPlayerView!!.setOnClickListener(null)
            miniPlayerView!!.isClickable = true
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
            if (miniPlayerView!!.visibility != View.VISIBLE) {
                miniPlayerView!!.visibility = View.VISIBLE
            }

            val duracion = musicaService!!.getDuracionTotal()
            if (duracion > 0) miniSeekBar?.max = duracion

            val db = AdminSQL(this)
            val cancion = db.obtenerTodasLasCanciones().find { it.recursoRaw == nombreRaw }

            if (cancion != null) {
                tvTitulo.text = cancion.titulo
                tvTitulo.isSelected = true
                tvArtista.text = cancion.artista
                val resId = resources.getIdentifier(cancion.imagenUri, "drawable", packageName)
                if (resId != 0) ivImagen.setImageResource(resId)
            }

            btnPlay.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else R.drawable.boton_de_play)
            if (isPlaying) iniciarActualizacionSlider()
        } else {
            miniPlayerView!!.visibility = View.GONE
        }
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
    //  TRANSICIONES Y POKEBALL (LÓGICA RESTAURADA DE POKEBALLACTIVITY)
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
        animarApertura() // <--- LLAMADA LIMPIA COMO EN POKEBALLACTIVITY
    }

    private fun animarApertura() {
        layoutTransicion.visibility = View.VISIBLE

        layoutTransicion.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                layoutTransicion.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val distanciaBoton = getDistanciaCentroBoton()
                val magnitudApertura = abs(distanciaBoton)

                // ESTADO INICIAL (Cerrado) - EXACTAMENTE COMO EN POKEBALLACTIVITY
                cortinaRoja.translationY = 0f
                cortinaBlanca.translationY = 0f
                btnPokeballCentral.translationY = distanciaBoton

                // 1. Mover bola a su posición final (abajo)
                btnPokeballCentral.animate()
                    .translationY(0f)
                    .rotation(0f)
                    .setDuration(600) // Duración original
                    .setInterpolator(OvershootInterpolator(1.0f))
                    .start()

                // 2. Abrir cortina roja (se va hacia arriba)
                cortinaRoja.animate()
                    .translationY(-magnitudApertura)
                    .setDuration(600)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()

                // 3. Abrir cortina blanca (se va hacia abajo)
                cortinaBlanca.animate()
                    .translationY(magnitudApertura)
                    .setDuration(600)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            layoutTransicion.visibility = View.GONE
                            animarEntradaTitulo()
                        }
                    }).start()
            }
        })
    }

    private fun getDistanciaCentroBoton(): Float {
        // Cálculo exacto de PokeballActivity
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

    // =========================================================
    //  NAVEGACIÓN Y MENÚ (IGUAL QUE POKEBALLACTIVITY)
    // =========================================================

    private fun setupLogicaMenu() {
        btnPokeballCentral.setOnClickListener { animarMenuAbanico() }
        fabOpcion1.setOnClickListener { if (this !is CatalogoActivity) navegarConAnimacion(CatalogoActivity::class.java) }
        fabOpcion2.setOnClickListener { if (this !is MainActivity) navegarConAnimacion(MainActivity::class.java) }
        fabOpcion3.setOnClickListener { if (this !is RankingActivity) navegarConAnimacion(RankingActivity::class.java) }
    }

    private fun animarMenuAbanico() {
        val radio = 85f * resources.displayMetrics.density
        if (isMenuAbierto) {
            btnPokeballCentral.animate().rotation(0f).setDuration(300).start()
            cerrarFab(fabOpcion1); cerrarFab(fabOpcion2); cerrarFab(fabOpcion3)
        } else {
            val interpolador = OvershootInterpolator(1.2f)
            btnPokeballCentral.animate().rotation(45f).setInterpolator(interpolador).setDuration(300).start()
            abrirFab(fabOpcion1, -radio * 0.85f, -radio * 0.5f, -30f, interpolador)
            abrirFab(fabOpcion2, 0f, -radio * 0.9f, 0f, interpolador)
            abrirFab(fabOpcion3, radio * 0.85f, -radio * 0.5f, 30f, interpolador)
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
        fab.animate().translationX(0f).translationY(0f).rotation(0f).alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(250).withEndAction { fab.visibility = View.INVISIBLE }.start()
        fab.isClickable = false
    }

    protected fun navegarConAnimacion(claseDestino: Class<*>) {
        layoutTransicion.visibility = View.VISIBLE
        btnPokeballCentral.bringToFront()

        tvTituloHeader?.animate()?.alpha(0f)?.setDuration(200)?.start()
        if (isMenuAbierto) animarMenuAbanico()

        val distanciaBoton = getDistanciaCentroBoton()
        val magnitudApertura = abs(distanciaBoton)

        // IMPORTANTE: Preparar cortinas FUERA (Abiertas) para cerrarlas
        cortinaRoja.translationY = -magnitudApertura
        cortinaBlanca.translationY = magnitudApertura

        // 1. Mover bola al centro
        btnPokeballCentral.animate()
            .translationY(distanciaBoton)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // 2. Cerrar cortina roja (baja a 0)
        cortinaRoja.animate().translationY(0f).setDuration(400).setInterpolator(AccelerateDecelerateInterpolator()).start()

        // 3. Cerrar cortina blanca (sube a 0)
        cortinaBlanca.animate().translationY(0f).setDuration(400).setInterpolator(AccelerateDecelerateInterpolator()).setListener(object : AnimatorListenerAdapter() {
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

    // CICLO DE VIDA
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