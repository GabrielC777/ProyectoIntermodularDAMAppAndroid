package com.example.musicsearch

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CancionAdapter(
    private var listaCancion: List<Cancion>,
    private val onClickListener: (Cancion) -> Unit
) : RecyclerView.Adapter<CancionAdapter.CancionViewHolder>() {

    // 1. VARIABLES DE ESTADO (Para saber qué canción está activa)
    private var nombreCancionSonando: String? = null
    private var esReproduciendo: Boolean = false

    // 2. MÉTODO PARA RECIBIR ESTADO DESDE MAINACTIVITY
    fun actualizarEstadoMusica(nombreRaw: String?, jugando: Boolean) {
        this.nombreCancionSonando = nombreRaw
        this.esReproduciendo = jugando
        notifyDataSetChanged() // Refresca la lista para pintar los nuevos iconos
    }

    fun actualizarLista(nuevaLista: List<Cancion>) {
        listaCancion = nuevaLista
        notifyDataSetChanged()
    }

    inner class CancionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivImagen: ImageView = itemView.findViewById(R.id.ivItemImagen)
        val tvTitulo: TextView = itemView.findViewById(R.id.tvItemNombre)

        // Asegúrate de tener estos IDs en tu item_card.xml
        val tvId: TextView = itemView.findViewById(R.id.tvItemId)
        val tvArtista: TextView = itemView.findViewById(R.id.tvItemTipo1)
        val tvVisitas: TextView = itemView.findViewById(R.id.tvItemTipo2)

        // Este es el icono de la derecha (flecha o play)
        val ivEstado: ImageView = itemView.findViewById(R.id.ivFlecha)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CancionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card, parent, false)
        return CancionViewHolder(view)
    }

    override fun onBindViewHolder(holder: CancionViewHolder, position: Int) {
        val cancion = listaCancion[position]

        // --- DATOS DE TEXTO ---
        holder.tvTitulo.text = cancion.titulo

        // Manejo seguro por si tvId no está en el layout, si da error quita esta línea
        try { holder.tvId.text = "#${cancion.id}" } catch (e: Exception) {}

        holder.tvArtista.text = cancion.artista
        holder.tvArtista.background.setTint(Color.parseColor("#424242"))

        holder.tvVisitas.visibility = View.VISIBLE
        holder.tvVisitas.text = "${cancion.visitas} 🎧"
        holder.tvVisitas.background.setTint(Color.parseColor("#009688"))

        // --- CARGAR IMAGEN ---
        val context = holder.itemView.context
        val resourceId = context.resources.getIdentifier(cancion.imagenUri, "drawable", context.packageName)

        if (resourceId != 0) {
            holder.ivImagen.setImageResource(resourceId)
        } else {
            holder.ivImagen.setImageResource(R.drawable.ic_launcher_foreground)
        }

        // --- LÓGICA VISUAL PLAY/PAUSE ---
        // Comparamos el recursoRaw de esta fila con el que dice el servicio que suena
        if (cancion.recursoRaw == nombreCancionSonando) {
            if (esReproduciendo) {
                // Es la canción actual y suena -> ICONO PAUSE
                holder.ivEstado.setImageResource(android.R.drawable.ic_media_pause)
                // Opcional: Resaltar título
                holder.tvTitulo.setTextColor(Color.parseColor("#00FFFF")) // Cyan
            } else {
                // Es la canción actual pero en pausa -> ICONO PLAY (o Resume)
                holder.ivEstado.setImageResource(android.R.drawable.ic_media_play)
                holder.tvTitulo.setTextColor(Color.parseColor("#00FFFF"))
            }
        } else {
            // No es la canción actual -> ICONO PLAY NORMAL (o tu flecha por defecto)
            holder.ivEstado.setImageResource(R.drawable.boton_de_play) // O R.drawable.ic_flecha_derecha
            holder.tvTitulo.setTextColor(Color.WHITE)
        }

        holder.itemView.setOnClickListener {
            onClickListener(cancion)
        }
    }

    override fun getItemCount(): Int = listaCancion.size
}