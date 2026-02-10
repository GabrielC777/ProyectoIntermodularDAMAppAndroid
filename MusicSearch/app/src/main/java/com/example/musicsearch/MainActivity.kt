package com.example.musicsearch

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Heredamos de MusicBaseActivity.
// Esto hace que, MainActivity heredemos el MiniPlayer,
// el menú flotante y la gestión del ServiceConnection (para el MusicaService)
class MainActivity : MusicBaseActivity() {

    // Componentes de la UI. Usamos lateinit para inicializarlos en onCreate.
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CancionAdapter
    private lateinit var etBuscador: EditText

    // Instanciamos de nuestra base de datos SQLite para las consultas
    private lateinit var db: AdminSQL

    // Dos listas:
    // 1. listaCompleta: La fuente de la verdad (todos los datos de la DB).
    // 2. listaFiltrada: Lo que realmente ve el usuario esta cambia al escribir en el buscador.
    private var listaCompleta: ArrayList<Cancion> = ArrayList()
    private var listaFiltrada: ArrayList<Cancion> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) //Referenciamos nuestro xml

        // Método de la clase padre (MusicBaseActivity).
        // Configura la "Pokeball" el titulo del marco, oculta la ActionBar y prepara las transiciones.
        setupPokeballUi("MUSIC PLAYER")

        // Referencias a las vistas del layout
        etBuscador = findViewById(R.id.etBuscador)
        recyclerView = findViewById(R.id.recyclerPokemon)

        // Configuramos el Recycler para que sea una lista vertical estándar
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Inicializamos la DB y cargamos los datos en memoria
        db = AdminSQL(this)
        actualizarDatosDB()

        // Configuramos el Adapter (el puente entre los datos y la vista).
        // Le pasamos la lista filtrada y una LAMBDA para manejar el click.
        adapter = CancionAdapter(listaFiltrada) { cancionSeleccionada ->
            manejarClickCancion(cancionSeleccionada)
        }
        recyclerView.adapter = adapter

        // Iniciamos el listener del EditText para el filtrado en tiempo real
        setupBuscador()
    }

    // --- COMUNICACIÓN CON EL SERVICIO ---

    // Este callback viene de MusicBaseActivity.
    // Se ejecuta SOLAMENTE cuando el MusicaService ya está conectado y listo.
    // Para pintar el estado inicial (si ya hay música sonando al abrir la app).
    override fun onMusicaServiceConnected() {
        super.onMusicaServiceConnected()
        actualizarEstadoAdapter()
    }

    // Pide al servicio qué está sonando y actualiza el Adapter.
    // El Adapter usará esto para pintar de color cian el título de la canción activa.
    private fun actualizarEstadoAdapter() {
        if (musicaService != null) {
            val nombre = musicaService?.getNombreCancion()?: "Sin valor"
            val sonando = musicaService?.isPlaying()?: false
            adapter.actualizarEstadoMusica(nombre, sonando)
        }
    }

    // --- LÓGICA DE REPRODUCCIÓN ---

    // Aquí está la "inteligencia" del click:
    private fun manejarClickCancion(cancion: Cancion) {
        // Safety check: si el servicio murió o no conectó, salimos para no crashear.
        if (musicaService == null) return

        val nombreSonando = musicaService?.getNombreCancion()?: "Sin valor"
        val estaSonando = musicaService?.isPlaying()?: false

        if (cancion.recursoRaw == nombreSonando) {
            // ESCENARIO 1: El usuario pulsó la misma canción que ya suena.
            // Hacemos una de estas dos cosas (Pausa/Resume) respectivamente.
            if (estaSonando) {
                // Mandamos orden de PAUSE al servicio
                val intentMusic = Intent(this, MusicaService::class.java) //Creamos la orden para el servicio
                intentMusic.action = "PAUSE" //Le indicamos que queremos
                startService(intentMusic) //Lo enviamos
                // Feedback visual inmediato (sin esperar al callback del servicio para que se sienta rápido)
                adapter.actualizarEstadoMusica(nombreSonando, false)
            } else {
                // Mandamos orden de RESUME
                val intentMusic = Intent(this, MusicaService::class.java)
                intentMusic.action = "RESUME"
                startService(intentMusic)
                adapter.actualizarEstadoMusica(nombreSonando, true)
            }
        } else {
            // ESCENARIO 2: El usuario pulsó una canción nueva.
            // Buscamos el ID numérico del recurso RAW
            val resID = resources.getIdentifier(cancion.recursoRaw, "raw", packageName)

            if (resID != 0) {
                // Mandamos orden de CAMBIAR_CANCION con el nuevo ID
                val intentMusic = Intent(this, MusicaService::class.java)
                intentMusic.action = "CAMBIAR_CANCION"
                intentMusic.putExtra("ID_CANCION", resID)
                startService(intentMusic)

                // Sumamos una visita en la BBDD para las estadísticas
                db.sumarVisita(cancion.id)

                // Actualizamos el Adapter para que cambie los valores actuales a la de la nueva canción
                adapter.actualizarEstadoMusica(cancion.recursoRaw, true)
            }
        }
    }

    // --- MÉTODOS HELPERS ---

    // Refresca la lista completa desde la base de datos.
    private fun actualizarDatosDB() {
        listaCompleta = db.obtenerTodasLasCanciones()
        listaFiltrada.clear()
        listaFiltrada.addAll(listaCompleta)
    }

    // Configura el filtro de búsqueda.
    private fun setupBuscador() {
        //Este metodo se ejecuta cada vez que se escriba algo en el EditText
        etBuscador.addTextChangedListener(object : TextWatcher {
            // No usamos estos dos, pero la interfaz obliga a implementarlos
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            // Después de editar el texto
            override fun afterTextChanged(s: Editable?) {
                val texto = s.toString().lowercase().trim()
                listaFiltrada.clear()
                if (texto.isEmpty()) {
                    // Si esta vacia, restauramos la lista completa
                    listaFiltrada.addAll(listaCompleta)
                } else {
                    // Filtramos por título o por artista usando el método filter
                    listaFiltrada.addAll(listaCompleta.filter {
                        it.titulo.lowercase().contains(texto) || it.artista.lowercase().contains(texto)
                    })
                }
                // Notificamos al adapter que la data cambió para que actualize la lista
                adapter.actualizarLista(listaFiltrada)
            }
        })
    }
    // Ciclo de vida: Se llama al volver a la app (ej: tras minimizar o volver de Detalles).
    override fun onResume() {
        super.onResume()
        // Recargamos datos por si cambiaron en otra pantalla
        actualizarDatosDB()
        adapter.actualizarLista(listaFiltrada)

        // Sincronizamos el estado visual (play/pause) por si cambió en segundo plano
        actualizarEstadoAdapter()
    }
}