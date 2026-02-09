package com.example.musicsearch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CatalogoActivity : MusicBaseActivity() { // Hereda de MusicBase para el menú y fondo

    private lateinit var adapter: GridAdapter
    private var listaCompleta: List<Cancion> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalogo)

        try { setupPokeballUi("BIBLIOTECA") } catch (e: Exception) {}

        val db = AdminSQL(this)
        listaCompleta = db.obtenerTodasLasCanciones()

        // 1. CONFIGURAR RECYCLER COMO GRID (2 COLUMNAS)
        val recycler = findViewById<RecyclerView>(R.id.recyclerCatalogo)
        recycler.layoutManager = GridLayoutManager(this, 2) // <--- ESTO HACE LA MAGIA

        adapter = GridAdapter(listaCompleta) { cancion ->
            // Al hacer click, vamos a DETALLE (no reproduce directo, eso es para Main)
            val intent = Intent(this, DetalleActivity::class.java)
            intent.putExtra("ID_CANCION", cancion.id)
            startActivity(intent)
        }
        recycler.adapter = adapter

        // 2. GENERAR FILTROS (CHIPS)
        crearFiltros()
    }

    private fun crearFiltros() {
        val container = findViewById<LinearLayout>(R.id.containerChips)
        // Obtenemos géneros únicos de la lista
        val generos = listOf("TODOS") + listaCompleta.map { it.genero }.distinct()

        for (genero in generos) {
            val chip = TextView(this)
            chip.text = genero
            chip.setPadding(30, 15, 30, 15)
            chip.textSize = 14f
            chip.setTextColor(Color.WHITE)
            chip.setBackgroundResource(R.drawable.bg_tipo_chip) // Usa tu fondo de chip
            // Margen entre chips
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            chip.layoutParams = params

            chip.setOnClickListener {
                filtrarPorGenero(genero)
            }
            container.addView(chip)
        }
    }

    private fun filtrarPorGenero(genero: String) {
        if (genero == "TODOS") {
            adapter.actualizarLista(listaCompleta)
        } else {
            val filtrada = listaCompleta.filter { it.genero == genero }
            adapter.actualizarLista(filtrada)
        }
    }
}