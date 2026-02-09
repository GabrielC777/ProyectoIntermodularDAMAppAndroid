package com.example.musicsearch

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Clase de datos sencilla para las opciones del rodillo
data class OpcionRanking(
    val titulo: String,
    val icono: Int,
    val tipoFiltro: String // "PLAYS" o "LIKES"
)

class WheelAdapter(private val listaOpciones: List<OpcionRanking>) :
    RecyclerView.Adapter<WheelAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombreJuego)
        val ivIcono: ImageView = view.findViewById(R.id.ivIconoJuego)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wheel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvNombre.text = listaOpciones[position].titulo
        holder.ivIcono.setImageResource(listaOpciones[position].icono)
    }

    override fun getItemCount() = listaOpciones.size
}