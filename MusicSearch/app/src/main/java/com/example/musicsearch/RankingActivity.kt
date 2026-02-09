package com.example.musicsearch

import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class RankingActivity : MusicBaseActivity() {

    private lateinit var recyclerRanking: RecyclerView
    private lateinit var recyclerWheel: RecyclerView
    private lateinit var db: AdminSQL

    // Opciones del Rodillo
    private val opcionesRodillo = listOf(
        OpcionRanking("TOP PLAYS", R.drawable.boton_de_play, "PLAYS"),
        OpcionRanking("TOP LIKES", R.drawable.megusta, "LIKES")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ranking)

        setupPokeballUi("RANKING")

        recyclerRanking = findViewById(R.id.recyclerRanking)
        recyclerWheel = findViewById(R.id.recyclerWheel)
        db = AdminSQL(this)

        recyclerRanking.layoutManager = LinearLayoutManager(this)

        // --- CONFIGURACIÓN DEL RODILLO DESLIZANTE ---
        configurarRodillo()

        // Carga inicial
        cargarRanking("PLAYS")
    }

    private fun configurarRodillo() {
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerWheel.layoutManager = layoutManager
        recyclerWheel.adapter = WheelAdapter(opcionesRodillo)

        // 1. SNAP HELPER (Efecto Imán)
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(recyclerWheel)

        // 2. PADDING PARA CENTRADO PERFECTO
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        // 140dp es el ancho del item en xml + unos 10dp de margen visual
        val itemWidthPx = (150 * Resources.getSystem().displayMetrics.density).toInt()
        val padding = (screenWidth / 2) - (itemWidthPx / 2)

        recyclerWheel.setPadding(padding, 0, padding, 0)
        recyclerWheel.clipToPadding = false

        // 3. LISTENERS DE SCROLL (Animación y Selección)
        recyclerWheel.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            // Cuando para de rodar -> Seleccionar opción
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val centerView = snapHelper.findSnapView(layoutManager)
                    if (centerView != null) {
                        val pos = layoutManager.getPosition(centerView)
                        if (pos != RecyclerView.NO_POSITION && pos < opcionesRodillo.size) {
                            val seleccion = opcionesRodillo[pos]
                            cargarRanking(seleccion.tipoFiltro)
                        }
                    }
                }
            }

            // Mientras rueda -> Efecto Escala y Transparencia
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val centroPantalla = recyclerView.width / 2f

                for (i in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(i)
                    val centroItem = (child.left + child.right) / 2f
                    val distancia = abs(centroPantalla - centroItem)

                    // Factor: 0 = centro, 1 = borde
                    var factor = distancia / (recyclerView.width / 2f)
                    if (factor > 1) factor = 1f

                    // Fórmula mágica de tu código original
                    child.alpha = 1f - (0.5f * factor)   // Se desvanece
                    child.scaleX = 1f - (0.3f * factor)  // Se encoge
                    child.scaleY = 1f - (0.3f * factor)
                }
            }
        })

        // Truco para centrar el primero al abrir
        recyclerWheel.post {
            // Pequeño scroll imperceptible para disparar el onScrolled inicial
            recyclerWheel.scrollBy(1, 0)
        }
    }

    private fun cargarRanking(filtro: String) {
        val listaCompleta = if (filtro == "LIKES") db.obtenerTopLikes() else db.obtenerTopCanciones()
        val esRankingLikes = (filtro == "LIKES")

        // Actualizamos Podio y Lista
        actualizarPodio(listaCompleta)

        val listaRestante = if (listaCompleta.size > 3) listaCompleta.subList(3, listaCompleta.size) else emptyList()
        recyclerRanking.adapter = RankingAdapter(listaRestante, esRankingLikes)
    }

    private fun actualizarPodio(lista: List<Cancion>) {
        val view1 = findViewById<View>(R.id.podium1)
        val view2 = findViewById<View>(R.id.podium2)
        val view3 = findViewById<View>(R.id.podium3)

        if (lista.isEmpty()) {
            view1.visibility = View.INVISIBLE
            view2.visibility = View.INVISIBLE
            view3.visibility = View.INVISIBLE
            return
        }

        // Top 1
        view1.visibility = View.VISIBLE
        configurarItemPodio(view1, lista[0])

        // Top 2
        if (lista.size > 1) {
            view2.visibility = View.VISIBLE
            configurarItemPodio(view2, lista[1])
        } else view2.visibility = View.INVISIBLE

        // Top 3
        if (lista.size > 2) {
            view3.visibility = View.VISIBLE
            configurarItemPodio(view3, lista[2])
        } else view3.visibility = View.INVISIBLE
    }

    private fun configurarItemPodio(view: View, cancion: Cancion) {
        // Buscamos las vistas dentro del layout del podio
        // Usamos IDs directos sabiendo cuál es cuál
        val tvNombre = view.findViewById<TextView>(
            when (view.id) {
                R.id.podium1 -> R.id.tvTop1Name
                R.id.podium2 -> R.id.tvTop2Name
                else -> R.id.tvTop3Name
            }
        )
        val ivImagen = view.findViewById<ImageView>(
            when (view.id) {
                R.id.podium1 -> R.id.ivTop1
                R.id.podium2 -> R.id.ivTop2
                else -> R.id.ivTop3
            }
        )

        tvNombre.text = cancion.titulo
        val resourceId = resources.getIdentifier(cancion.imagenUri, "drawable", packageName)
        if (resourceId != 0) ivImagen.setImageResource(resourceId)
        else ivImagen.setImageResource(R.drawable.ic_launcher_foreground)
    }
}