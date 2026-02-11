package com.example.musicsearch

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// ADAPTER: COLA DE REPRODUCCIÓN
// Este adapter es "minimalista". No necesita cargar imágenes ni gestionar botones complejos.
// Solo muestra texto para que el usuario vea qué va a sonar después.
class ColaAdapter(private val lista: List<Cancion>) : RecyclerView.Adapter<ColaAdapter.Holder>() {

    // ViewHolder Simplificado
    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val t1: TextView = v.findViewById(android.R.id.text1) // La línea de arriba (Título)
        val t2: TextView = v.findViewById(android.R.id.text2) // La línea de abajo (Subtítulo)
    }
    //Creamos una vista
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        // Es un layout que vive dentro del sistema Android y ya tiene dos TextViews listos para usar.
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)

        // DISEÑO UI:
        // Este layout puede tener fondo blanco ponemos TRANSPARENTE
        v.setBackgroundColor(Color.TRANSPARENT)

        return Holder(v)
    }

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val cancion = lista[pos]

        // Configuración Visual:
        // Queremos jerarquía visual: El título destaca, el artista acompaña.

        // 1. Título: Color Blanco Brillante
        h.t1.text = cancion.titulo
        h.t1.setTextColor(Color.WHITE)

        // 2. Artista: Color Gris Claro (Light Gray)
        // Esto indica visualmente que es información secundaria.
        h.t2.text = cancion.artista
        h.t2.setTextColor(Color.LTGRAY)
    }

    // Devuelve cuántas canciones hay pendientes en la cola.
    override fun getItemCount() = lista.size
}