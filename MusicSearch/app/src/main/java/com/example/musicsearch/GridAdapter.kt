package com.example.musicsearch

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GridAdapter(
    private var lista: List<Cancion>,
    private val onClick: (Cancion) -> Unit
) : RecyclerView.Adapter<GridAdapter.GridViewHolder>() {

    class GridViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imagen: ImageView = view.findViewById(R.id.ivGridImagen)
        val titulo: TextView = view.findViewById(R.id.tvGridTitulo)
        val artista: TextView = view.findViewById(R.id.tvGridArtista)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grid, parent, false)
        return GridViewHolder(view)
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        val item = lista[position]
        holder.titulo.text = item.titulo
        holder.artista.text = item.artista

        val resId = holder.itemView.context.resources.getIdentifier(item.imagenUri, "drawable", holder.itemView.context.packageName)
        if (resId != 0) holder.imagen.setImageResource(resId)

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = lista.size

    fun actualizarLista(nueva: List<Cancion>) {
        lista = nueva
        notifyDataSetChanged()
    }
}