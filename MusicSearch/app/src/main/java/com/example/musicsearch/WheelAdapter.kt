package com.example.musicsearch

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Clase de datos: Define la estructura de cada opción en el rodillo (Texto, Icono y Clave de filtro).
data class OpcionRanking(
    val titulo: String,
    val icono: Int,
    val tipoFiltro: String // "PLAYS" o "LIKES"
)

// WheelAdapter: Gestiona las opciones del menú circular/rodillo del Ranking.
class WheelAdapter(private val listaOpciones: List<OpcionRanking>) :
    RecyclerView.Adapter<WheelAdapter.ViewHolder>() {

    // ViewHolder: Memoriza las vistas de cada opción (el icono y el nombre del filtro).
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombreJuego) // Reutilizamos ID: Antes NombreJuego -> Ahora Filtro
        val ivIcono: ImageView = view.findViewById(R.id.ivIconoJuego)  // Reutilizamos ID: Antes IconoJuego -> Ahora Icono
    }

    // Infla el diseño XML 'item_wheel.xml' para crear la vista física de cada opción.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wheel, parent, false)
        return ViewHolder(view)
    }

    // Une los datos de la opción (Top Plays / Top Likes) con la vista.
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val opcion = listaOpciones[position]

        holder.tvNombre.text = opcion.titulo
        holder.ivIcono.setImageResource(opcion.icono)
    }

    // Devuelve cuántas opciones hay en el rodillo (en este caso, 2).
    override fun getItemCount() = listaOpciones.size
}