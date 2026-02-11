package com.example.musicsearch

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Clase Adapter para la cuadrícula (Grid): Muestra las canciones en un formato tipo "mosaico".
// Recibe:
// 1. lista: Los datos a mostrar.
// 2. onClick: La acción que se dispara al tocar un elemento del grid.
class GridAdapter(
    private var lista: List<Cancion>,
    private val onClick: (Cancion) -> Unit
) : RecyclerView.Adapter<GridAdapter.GridViewHolder>() {

    // ViewHolder: "Guarda" las referencias de las vistas del diseño item_grid.xml.
    // Así el sistema no tiene que buscarlas por ID cada vez, ahorrando batería y memoria.
    class GridViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imagen: ImageView = view.findViewById(R.id.ivGridImagen)
        val titulo: TextView = view.findViewById(R.id.tvGridTitulo)
        val artista: TextView = view.findViewById(R.id.tvGridArtista)
    }

    // Crea la vista física: Infla el layout XML para cada celda de la cuadrícula.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grid, parent, false)
        return GridViewHolder(view)
    }

    // Une los datos: Pone la info de la canción en los elementos visuales creados.
    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        val item = lista[position]
        val context = holder.itemView.context

        // Asignamos textos básicos: Nombre de canción y cantante.
        holder.titulo.text = item.titulo
        holder.artista.text = item.artista

        // Buscamos la imagen: Convierte el nombre del archivo (String) en un ID que Android entienda.
        val resId = context.resources.getIdentifier(item.imagenUri, "drawable", context.packageName)
        if (resId != 0) holder.imagen.setImageResource(resId)

        // Click: Cuando tocan la celda, ejecuta la función onClick pasando la canción elegida.
        holder.itemView.setOnClickListener { onClick(item) }
    }

    // Devuelve el tamaño total de la lista.
    override fun getItemCount() = lista.size

    // Método para el Buscador: Actualiza la cuadrícula con los resultados filtrados.
    fun actualizarLista(nueva: List<Cancion>) {
        lista = nueva
        notifyDataSetChanged() // Refresca visualmente todo el Grid.
    }
}