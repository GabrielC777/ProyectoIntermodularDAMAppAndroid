package com.example.musicsearch

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RankingAdapter(
    private var listaCanciones: List<Cancion>,
    private var esRankingLikes: Boolean // Flag para saber qué mostrar (Likes o Plays)
) : RecyclerView.Adapter<RankingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layoutFila: LinearLayout = view.findViewById(R.id.layoutFilaRanking)
        val tvPos: TextView = view.findViewById(R.id.tvPosicion)
        val tvTitulo: TextView = view.findViewById(R.id.tvNombreJugador) // Reutilizamos ID: Antes NombreJugador -> Ahora Título
        val tvDatos: TextView = view.findViewById(R.id.tvPuntosJugador) // Reutilizamos ID: Antes Puntos -> Ahora Plays/Likes
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ranking_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cancion = listaCanciones[position]

        // CAMBIO: Sumamos 4 porque los 3 primeros están en el podio
        val puesto = position + 4

        holder.tvPos.text = "#$puesto"

        // Mostramos Título y un poco del Artista
        holder.tvTitulo.text = "${cancion.titulo} - ${cancion.artista}"

        // --- LÓGICA DINÁMICA: ¿PLAYS O LIKES? ---
        if (esRankingLikes) {
            holder.tvDatos.text = "${cancion.meGusta} ❤"
            holder.tvDatos.setTextColor(Color.parseColor("#E91E63")) // Rosa
        } else {
            holder.tvDatos.text = "${cancion.visitas} 🎧"
            holder.tvDatos.setTextColor(Color.parseColor("#009688")) // Verde
        }

        // --- ESTILO VISUAL ---
        val background = holder.layoutFila.background as GradientDrawable

        if (puesto <= 3) {
            // --- TOP 3 (DESTACADO) ---
            background.setColor(Color.parseColor("#33FFFFFF")) // Fondo más claro
            background.setStroke(2, if(esRankingLikes) Color.parseColor("#E91E63") else Color.parseColor("#009688")) // Borde de color

            holder.tvPos.setTextColor(Color.WHITE)
            holder.tvTitulo.setTextColor(Color.WHITE)
        } else {
            // --- RESTO DE LA LISTA ---
            background.setColor(Color.parseColor("#15FFFFFF")) // Fondo muy sutil
            background.setStroke(0, Color.TRANSPARENT)

            holder.tvPos.setTextColor(Color.parseColor("#B0BEC5")) // Gris
            holder.tvTitulo.setTextColor(Color.parseColor("#E0E0E0")) // Blanco roto
        }
    }

    override fun getItemCount() = listaCanciones.size

    // Método para actualizar la lista desde la Activity cuando gira el rodillo
    fun actualizarLista(nuevaLista: List<Cancion>, esLikes: Boolean) {
        listaCanciones = nuevaLista
        esRankingLikes = esLikes
        notifyDataSetChanged()
    }
}