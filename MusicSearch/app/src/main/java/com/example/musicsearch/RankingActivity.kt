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

// RankingActivity: Muestra las canciones más populares (por reproducciones o likes).
// Hereda de MusicBaseActivity para mantener el MiniPlayer y las transiciones.
class RankingActivity : MusicBaseActivity() {

    private lateinit var recyclerRanking: RecyclerView // Lista para el resto del ranking (puestos 4+)
    private lateinit var recyclerWheel: RecyclerView   // El rodillo horizontal para elegir el filtro
    private lateinit var db: AdminSQL // Conexión a la base de datos

    // Opciones del Rodillo: Define qué filtros podemos aplicar (Vistas o Me Gustas)
    private val opcionesRodillo = listOf(
        OpcionRanking("TOP PLAYS", R.drawable.boton_de_play, "PLAYS"),
        OpcionRanking("TOP LIKES", R.drawable.megusta, "LIKES")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ranking)

        // Configura la interfaz base (Pokeball, título y barra de estado)
        setupMarcoUi("RANKING")

        recyclerRanking = findViewById(R.id.recyclerRanking)
        recyclerWheel = findViewById(R.id.recyclerWheel)
        db = AdminSQL(this)

        recyclerRanking.layoutManager = LinearLayoutManager(this)

        // --- CONFIGURACIÓN DEL RODILLO DESLIZANTE ---
        configurarRodillo()

        // Carga inicial: Por defecto mostramos el ranking de reproducciones
        cargarRanking("PLAYS")
    }

    // Lógica del Rodillo (Wheel): Configura el menú horizontal con efecto de imán y escala.
    private fun configurarRodillo() {
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerWheel.layoutManager = layoutManager
        recyclerWheel.adapter = WheelAdapter(opcionesRodillo)

        // 1. SNAP HELPER: Hace que los elementos siempre queden centrados (efecto imán).
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(recyclerWheel)

        // 2. CENTRADO PERFECTO: Calcula el padding necesario para que el primer item quede en el centro.
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val itemWidthPx = (150 * Resources.getSystem().displayMetrics.density).toInt()
        val padding = (screenWidth / 2) - (itemWidthPx / 2)

        // Aplicamos el padding para que el contenido no choque con los bordes
        recyclerWheel.setPadding(padding, 0, padding, 0)
        recyclerWheel.clipToPadding = false

        // 3. LISTENERS DE SCROLL: Controlan la selección y las animaciones visuales mientras ruedas.
        recyclerWheel.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            // Se dispara cuando el rodillo deja de moverse: Detecta qué opción quedó en el centro.
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val centerView = snapHelper.findSnapView(layoutManager)
                    if (centerView != null) {
                        val pos = layoutManager.getPosition(centerView)
                        if (pos != RecyclerView.NO_POSITION && pos < opcionesRodillo.size) {
                            val seleccion = opcionesRodillo[pos]
                            cargarRanking(seleccion.tipoFiltro) // Actualiza la lista según el filtro
                        }
                    }
                }
            }

            // Mientras rueda: Calcula la distancia al centro para encoger o desvanecer los items.
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val centroPantalla = recyclerView.width / 2f

                for (i in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(i)
                    val centroItem = (child.left + child.right) / 2f
                    val distancia = abs(centroPantalla - centroItem)

                    // Factor de deformación: 0 en el centro, 1 en los bordes.
                    var factor = distancia / (recyclerView.width / 2f)
                    if (factor > 1) factor = 1f

                    // Aplicamos efectos: Los que se alejan del centro se vuelven pequeños y transparentes.
                    child.alpha = 1f - (0.5f * factor)
                    child.scaleX = 1f - (0.3f * factor)
                    child.scaleY = 1f - (0.3f * factor)
                }
            }
        })

        // Pequeño truco para forzar el dibujado correcto del primer elemento al abrir la pantalla.
        recyclerWheel.post {
            recyclerWheel.scrollBy(1, 0)
        }
    }

    // Obtiene los datos de la DB y reparte las canciones entre el Podio y el Recycler.
    private fun cargarRanking(filtro: String) {
        val listaCompleta = if (filtro == "LIKES") db.obtenerTopLikes() else db.obtenerTopCanciones()
        val esRankingLikes = (filtro == "LIKES")

        // 1. Actualizamos visualmente el Top 3 (Podio)
        actualizarPodio(listaCompleta)

        // 2. El resto (puesto 4 en adelante) se manda al RecyclerView inferior
        val listaRestante = if (listaCompleta.size > 3) listaCompleta.subList(3, listaCompleta.size) else emptyList()
        recyclerRanking.adapter = RankingAdapter(listaRestante, esRankingLikes)
    }

    // Gestiona la visibilidad y datos de los 3 cajones del podio.
    private fun actualizarPodio(lista: List<Cancion>) {
        val view1 = findViewById<View>(R.id.podium1)
        val view2 = findViewById<View>(R.id.podium2)
        val view3 = findViewById<View>(R.id.podium3)

        // Si no hay datos, ocultamos todo
        if (lista.isEmpty()) {
            view1.visibility = View.INVISIBLE
            view2.visibility = View.INVISIBLE
            view3.visibility = View.INVISIBLE
            return
        }

        // Top 1: Siempre visible si la lista no está vacía
        view1.visibility = View.VISIBLE
        configurarItemPodio(view1, lista[0])

        // Top 2: Visible solo si hay al menos 2 canciones
        if (lista.size > 1) {
            view2.visibility = View.VISIBLE
            configurarItemPodio(view2, lista[1])
        } else view2.visibility = View.INVISIBLE

        // Top 3: Visible solo si hay al menos 3 canciones
        if (lista.size > 2) {
            view3.visibility = View.VISIBLE
            configurarItemPodio(view3, lista[2])
        } else view3.visibility = View.INVISIBLE
    }

    // Rellena la imagen y el título de un cajón del podio específico.
    private fun configurarItemPodio(view: View, cancion: Cancion) {
        // Buscamos los TextViews e ImageViews según qué puesto estemos configurando
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

        // Carga de imagen dinámica desde drawable
        val resourceId = resources.getIdentifier(cancion.imagenUri, "drawable", packageName)
        if (resourceId != 0) ivImagen.setImageResource(resourceId)
        else ivImagen.setImageResource(R.drawable.ic_launcher_foreground)
    }
}