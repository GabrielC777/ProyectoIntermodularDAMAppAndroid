package com.example.musicsearch

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : MusicBaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CancionAdapter
    private lateinit var etBuscador: EditText
    private lateinit var db: AdminSQL
    private var listaCompleta: ArrayList<Cancion> = ArrayList()
    private var listaFiltrada: ArrayList<Cancion> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupPokeballUi("MUSIC PLAYER")

        etBuscador = findViewById(R.id.etBuscador)
        recyclerView = findViewById(R.id.recyclerPokemon) // Ojo con este ID, asegúrate que es el de tu xml
        recyclerView.layoutManager = LinearLayoutManager(this)

        db = AdminSQL(this)

        // Cargar datos
        actualizarDatosDB()

        // Configurar Adapter con la lógica inteligente
        adapter = CancionAdapter(listaFiltrada) { cancionSeleccionada ->
            manejarClickCancion(cancionSeleccionada)
        }
        recyclerView.adapter = adapter

        setupBuscador()
    }

    // --- SINCRONIZACIÓN CON EL SERVICIO ---

    // 1. Al conectar (al abrir la app), actualizamos los iconos
    override fun onMusicaServiceConnected() {
        super.onMusicaServiceConnected()
        actualizarEstadoAdapter()
    }

    // 2. Método para refrescar los iconos según lo que diga el servicio
    private fun actualizarEstadoAdapter() {
        if (musicaService != null) {
            val nombre = musicaService!!.getNombreCancion()
            val jugando = musicaService!!.isPlaying()
            adapter.actualizarEstadoMusica(nombre, jugando)
        }
    }

    // 3. Lógica del Click (Inteligente)
    private fun manejarClickCancion(cancion: Cancion) {
        if (musicaService == null) return

        val nombreSonando = musicaService!!.getNombreCancion()
        val estaSonando = musicaService!!.isPlaying()

        if (cancion.recursoRaw == nombreSonando) {
            // -- CLICK EN LA MISMA CANCIÓN --
            if (estaSonando) {
                // Está sonando -> PAUSAR
                val intentMusic = Intent(this, MusicaService::class.java)
                intentMusic.action = "PAUSE"
                startService(intentMusic)
                // Actualizamos visualmente manual para que sea rápido
                adapter.actualizarEstadoMusica(nombreSonando, false)
            } else {
                // Está en pausa -> REANUDAR
                val intentMusic = Intent(this, MusicaService::class.java)
                intentMusic.action = "RESUME"
                startService(intentMusic)
                adapter.actualizarEstadoMusica(nombreSonando, true)
            }
        } else {
            // -- CLICK EN CANCIÓN NUEVA --
            val resID = resources.getIdentifier(cancion.recursoRaw, "raw", packageName)
            if (resID != 0) {
                val intentMusic = Intent(this, MusicaService::class.java)
                intentMusic.action = "CAMBIAR_CANCION"
                intentMusic.putExtra("ID_CANCION", resID) // Usamos ID, no String
                startService(intentMusic)

                // Sumar visita
                db.sumarVisita(cancion.id)

                // Actualizar visualmente (asumimos que empieza a sonar)
                adapter.actualizarEstadoMusica(cancion.recursoRaw, true)
            }
        }
    }

    // --- RESTO DE MÉTODOS ---

    private fun actualizarDatosDB() {
        listaCompleta = db.obtenerTodasLasCanciones()
        listaFiltrada.clear()
        listaFiltrada.addAll(listaCompleta)
    }

    private fun setupBuscador() {
        etBuscador.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val texto = s.toString().lowercase().trim()
                listaFiltrada.clear()
                if (texto.isEmpty()) {
                    listaFiltrada.addAll(listaCompleta)
                } else {
                    listaFiltrada.addAll(listaCompleta.filter {
                        it.titulo.lowercase().contains(texto) || it.artista.lowercase().contains(texto)
                    })
                }
                adapter.actualizarLista(listaFiltrada)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Recargar datos por si cambiaron visitas
        actualizarDatosDB()
        adapter.actualizarLista(listaFiltrada)

        // Y muy importante: Sincronizar estado del reproductor al volver
        actualizarEstadoAdapter()
    }
}