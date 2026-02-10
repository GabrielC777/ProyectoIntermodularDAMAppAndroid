package com.example.musicsearch

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class CancionAdapter(
    private var listaCancion: List<Cancion>,
    private val onClickListener: (Cancion) -> Unit
) : RecyclerView.Adapter<CancionAdapter.CancionViewHolder>() {

    private var nombreCancionSonando: String? = null
    private var esReproduciendo: Boolean = false

    fun actualizarEstadoMusica(nombreRaw: String?, jugando: Boolean) {
        this.nombreCancionSonando = nombreRaw
        this.esReproduciendo = jugando
        notifyDataSetChanged()
    }

    fun actualizarLista(nuevaLista: List<Cancion>) {
        listaCancion = nuevaLista
        notifyDataSetChanged()
    }

    inner class CancionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivImagen: ImageView = itemView.findViewById(R.id.ivItemImagen)
        val tvTitulo: TextView = itemView.findViewById(R.id.tvItemNombre)
        val tvId: TextView = itemView.findViewById(R.id.tvItemId)
        val tvArtista: TextView = itemView.findViewById(R.id.tvItemTipo1)
        val tvVisitas: TextView = itemView.findViewById(R.id.tvItemTipo2)
        val tvLikes: TextView = itemView.findViewById(R.id.tvItemLikes)
        val ivEstado: ImageView = itemView.findViewById(R.id.ivFlecha)
        val btnCola: ImageView = itemView.findViewById(R.id.btnCola)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CancionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return CancionViewHolder(view)
    }

    override fun onBindViewHolder(holder: CancionViewHolder, position: Int) {
        val cancion = listaCancion[position]
        val context = holder.itemView.context

        holder.tvTitulo.text = cancion.titulo
        try { holder.tvId.text = "#${cancion.id}" } catch (e: Exception) {}
        holder.tvArtista.text = cancion.artista
        holder.tvArtista.background?.setTint(Color.parseColor("#424242"))

        holder.tvVisitas.visibility = View.VISIBLE
        holder.tvVisitas.text = "${cancion.visitas} 🎧"
        holder.tvVisitas.background?.setTint(Color.parseColor("#009688"))

        holder.tvLikes.visibility = View.VISIBLE
        holder.tvLikes.text = formatearLikes(cancion.meGusta)

        val resId = context.resources.getIdentifier(cancion.imagenUri, "drawable", context.packageName)
        if (resId != 0) holder.ivImagen.setImageResource(resId)
        else holder.ivImagen.setImageResource(R.drawable.ic_launcher_foreground)

        // Estado Play/Pause
        if (cancion.recursoRaw == nombreCancionSonando) {
            holder.tvTitulo.setTextColor(Color.parseColor("#00FFFF"))
            holder.ivEstado.setImageResource(if (esReproduciendo) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        } else {
            holder.tvTitulo.setTextColor(Color.WHITE)
            holder.ivEstado.setImageResource(R.drawable.boton_de_play)
        }

        holder.itemView.setOnClickListener { onClickListener(cancion) }

        // BOTÓN COLA: Envía el Intent que el Servicio ahora SÍ escucha
        holder.btnCola.setOnClickListener {
            val idRaw = context.resources.getIdentifier(cancion.recursoRaw, "raw", context.packageName)
            if (idRaw != 0) {
                val intent = Intent(context, MusicaService::class.java)
                intent.action = "AGREGAR_COLA"
                intent.putExtra("ID_CANCION", idRaw)
                context.startService(intent)
                Toast.makeText(context, "Añadida a cola: ${cancion.titulo}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount(): Int = listaCancion.size

    private fun formatearLikes(likes: Int): String {
        return if (likes >= 1000) String.format("%.1fk", likes / 1000.0) else likes.toString()
    }
}