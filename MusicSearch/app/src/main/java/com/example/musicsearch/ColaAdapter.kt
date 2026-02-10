package com.example.musicsearch

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ColaAdapter(private val lista: List<Cancion>) : RecyclerView.Adapter<ColaAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        // Usamos los IDs por defecto del layout simple_list_item_2
        val t1: TextView = v.findViewById(android.R.id.text1)
        val t2: TextView = v.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        // Inflamos un layout simple de Android para la lista
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)

        // Hacemos el fondo transparente para que se vea bien sobre el diseño oscuro
        v.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        return Holder(v)
    }

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val cancion = lista[pos]

        // Título en Blanco
        h.t1.text = cancion.titulo
        h.t1.setTextColor(android.graphics.Color.WHITE)

        // Artista en Gris Claro
        h.t2.text = cancion.artista
        h.t2.setTextColor(android.graphics.Color.LTGRAY)
    }

    override fun getItemCount() = lista.size
}