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

        // UI Base (Pokeball, etc.)
        setupPokeballUi("MUSIC PLAYER")

        etBuscador = findViewById(R.id.etBuscador)
        recyclerView = findViewById(R.id.recyclerPokemon)
        recyclerView.layoutManager = LinearLayoutManager(this)

        db = AdminSQL(this)
        actualizarDatosDB()

        // Configuramos el Adapter
        adapter = CancionAdapter(listaFiltrada) { cancion ->
            manejarClickCancion(cancion)
        }
        recyclerView.adapter = adapter

        setupBuscador()
    }

    // AL CONECTAR CON EL SERVICIO: Sincronizar UI
    override fun onMusicaServiceConnected() {
        super.onMusicaServiceConnected()
        actualizarEstadoAdapter()
    }

    private fun actualizarEstadoAdapter() {
        if (musicaService != null) {
            val nombre = musicaService!!.getNombreCancion()
            val jugando = musicaService!!.isPlaying()
            adapter.actualizarEstadoMusica(nombre, jugando)
        }
    }

    private fun manejarClickCancion(cancion: Cancion) {
        if (musicaService == null) return

        val nombreSonando = musicaService!!.getNombreCancion()
        val estaSonando = musicaService!!.isPlaying()

        if (cancion.recursoRaw == nombreSonando) {
            // Misma canción: Play/Pause
            val action = if (estaSonando) "PAUSE" else "RESUME"
            startService(Intent(this, MusicaService::class.java).apply { this.action = action })
            // Actualización optimista inmediata
            adapter.actualizarEstadoMusica(nombreSonando, !estaSonando)
        } else {
            // Nueva canción
            val resID = resources.getIdentifier(cancion.recursoRaw, "raw", packageName)
            if (resID != 0) {
                val intent = Intent(this, MusicaService::class.java)
                intent.action = "CAMBIAR_CANCION"
                intent.putExtra("ID_CANCION", resID)
                startService(intent)

                db.sumarVisita(cancion.id)
                adapter.actualizarEstadoMusica(cancion.recursoRaw, true)
            }
        }
    }

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
        actualizarDatosDB()
        adapter.actualizarLista(listaFiltrada)
        actualizarEstadoAdapter()
    }
}