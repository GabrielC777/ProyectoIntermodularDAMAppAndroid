package com.example.musicsearch

import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

// Clase Adapter: Es el puente entre nuestros datos (Lista de Canciones) y la vista (RecyclerView).
// Se encarga de crear las "tarjetas" y rellenarlas con la info de cada canción.
// Recibe:
// 1. listaCancion: Los datos a mostrar.
// 2. onClickListener: Una función lambda para avisar al MainActivity cuando tocan una canción.
class CancionAdapter(
    private var listaCancion: List<Cancion>,
    private val onClickListener: (Cancion) -> Unit
) : RecyclerView.Adapter<CancionAdapter.CancionViewHolder>() {

    // Variables de estado para controlar la UI de la canción activa.
    // Nos sirven para pintar de color CIAN el título y cambiar el icono Play/Pause.
    private var nombreCancionSonando: String? = null
    private var esReproduciendo: Boolean = false

    // Método para comunicar el Adapter con el Servicio (a través del MainActivity).
    // Cuando la música cambia o se pausa, actualizamos estas variables y refrescamos la lista.
    fun actualizarEstadoMusica(nombreRaw: String?, jugando: Boolean) {
        this.nombreCancionSonando = nombreRaw
        this.esReproduciendo = jugando
        notifyDataSetChanged() // Fuerza a redibujar toda la lista para aplicar los colores nuevos.
    }

    // Método para el Buscador.
    // Recibe la lista filtrada desde el MainActivity y actualiza el Recycler.
    fun actualizarLista(nuevaLista: List<Cancion>) {
        listaCancion = nuevaLista
        notifyDataSetChanged()
    }

    // ViewHolder: Esta clase "memoriza" las referencias a los elementos del XML (item_card).
    // Evita que Android tenga que buscar (findViewById) los elementos cada vez que hacemos scroll, mejorando el rendimiento.
    inner class CancionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivImagen: ImageView = itemView.findViewById(R.id.ivItemImagen)
        val tvTitulo: TextView = itemView.findViewById(R.id.tvItemNombre)
        val tvId: TextView = itemView.findViewById(R.id.tvItemId)
        val tvArtista: TextView = itemView.findViewById(R.id.tvItemTipo1)
        val tvVisitas: TextView = itemView.findViewById(R.id.tvItemTipo2)
        val tvLikes: TextView = itemView.findViewById(R.id.tvItemLikes)
        val ivEstado: ImageView = itemView.findViewById(R.id.ivFlecha) // Icono dinámico (Play/Pause)
        val btnCola: ImageView = itemView.findViewById(R.id.btnCola)   // Botón pequeño para añadir a la cola
    }

    // Crea el diseño visual inflando el layout 'item_card.xml'.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CancionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card, parent, false)
        return CancionViewHolder(view)
    }

    // Enlaza los datos de una canción específica con las vistas del ViewHolder.
    override fun onBindViewHolder(holder: CancionViewHolder, position: Int) {
        val cancion = listaCancion[position]
        val context = holder.itemView.context // Necesario para acceder a los recursos (imágenes, colores)

        // Asignamos los textos básicos
        holder.tvTitulo.text = cancion.titulo
        try { holder.tvId.text = "#${cancion.id}" } catch (e: Exception)
        {
            Log.e("[DEBUG]", "Error en el onBindViewHolder$e")
        }

        // Estilización del Artista (fondo gris oscuro)
        holder.tvArtista.text = cancion.artista
        holder.tvArtista.background?.setTint(Color.parseColor("#424242"))

        // Estilización de Visitas (fondo verde teal)
        holder.tvVisitas.visibility = View.VISIBLE
        holder.tvVisitas.text = "${cancion.visitas} 🎧"
        holder.tvVisitas.background?.setTint(Color.parseColor("#009688"))

        // Estilización de Likes (usamos helper para formatear "1000" a "1k")
        holder.tvLikes.visibility = View.VISIBLE
        holder.tvLikes.text = formatearLikes(cancion.meGusta)

        // Carga dinámica de la imagen usando el nombre del archivo (string) para buscar su ID numérico
        val resId = context.resources.getIdentifier(cancion.imagenUri, "drawable", context.packageName)
        if (resId != 0) holder.ivImagen.setImageResource(resId)
        else holder.ivImagen.setImageResource(R.drawable.ic_launcher_foreground)

        // LÓGICA DE ESTADO VISUAL:
        // Comparamos si esta canción es la que está sonando actualmente en el Servicio.
        if (cancion.recursoRaw == nombreCancionSonando) {
            // Caso: Es la canción activa.
            holder.tvTitulo.setTextColor(Color.parseColor("#00FFFF")) // Color Cian
            // Alternamos icono Play/Pause según el estado real del MediaPlayer
            holder.ivEstado.setImageResource(if (esReproduciendo) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        } else {
            // Caso: No es la canción activa.
            holder.tvTitulo.setTextColor(Color.WHITE) // Color Blanco normal
            holder.ivEstado.setImageResource(R.drawable.boton_de_play) // Icono Play por defecto
        }

        // Click Principal: Reproducir canción.
        // Ejecuta la lambda que nos pasó el MainActivity.
        holder.itemView.setOnClickListener { onClickListener(cancion) }

        // Click Secundario: Añadir a la cola.
        // Si 'nombreCancionSonando' no es null, significa que el Servicio tiene música activa.
        val estaHabilitado = nombreCancionSonando != null

        // Controlamos la APARIENCIA
        if (estaHabilitado) {
            holder.btnCola.alpha = 1.0f      // Se ve totalmente opaco
            holder.btnCola.isEnabled = true
        } else {
            holder.btnCola.alpha = 0.3f      // Se ve con opacidad
            holder.btnCola.isEnabled = false
        }
        holder.btnCola.setOnClickListener {
            // Esto es una "doble seguridad" por si el RecyclerView recicló la vista.
            if (nombreCancionSonando != null) {

                // Buscamos el ID numérico del archivo de audio (el archivo en la carpeta 'raw')
                val idRaw = context.resources.getIdentifier(cancion.recursoRaw, "raw", context.packageName)

                if (idRaw != 0) {
                    // Creamos el Intent para comunicarnos con el MusicaService
                    val intent = Intent(context, MusicaService::class.java)
                    intent.action = "AGREGAR_COLA"     // Le decimos al servicio qué queremos hacer
                    intent.putExtra("ID_CANCION", idRaw) // Le pasamos la canción específica

                    // Enviamos la orden al Servicio
                    context.startService(intent)

                    // Avisamos al usuario con un mensaje rápido
                    Toast.makeText(context, "Añadida a cola: ${cancion.titulo}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Debes reproducir algo primero para usar la cola", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount(): Int = listaCancion.size

    // Helper para formatear números grandes (Ej: 1500 -> 1.5k)
    private fun formatearLikes(likes: Int): String {
        return if (likes >= 1000) String.format("%.1fk", likes / 1000.0) else likes.toString()
    }
}