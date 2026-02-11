package com.example.musicsearch

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// RankingAdapter: Gestiona la lista de canciones que aparecen debajo del Top 3.
// Recibe la lista de canciones y un flag (booleano) para decidir si muestra Likes o Reproducciones.
class RankingAdapter(
    private var listaCanciones: List<Cancion>,
    private var esRankingLikes: Boolean
) : RecyclerView.Adapter<RankingAdapter.ViewHolder>() {

    // ViewHolder: Referencia las vistas de cada fila.
    // Nota: Se reutilizaron IDs de un diseño anterior (Jugador/Puntos) para adaptarlos a la música.
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layoutFila: LinearLayout = view.findViewById(R.id.layoutFilaRanking) // El contenedor de la fila
        val tvPos: TextView = view.findViewById(R.id.tvPosicion) // Número del puesto (#4, #5...)
        val tvTitulo: TextView = view.findViewById(R.id.tvNombreJugador) // Mostrará Título - Artista
        val tvDatos: TextView = view.findViewById(R.id.tvPuntosJugador) // Mostrará el número de Likes o Plays
    }

    // Crea la vista física de la fila inflando el layout XML.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ranking_row, parent, false)
        return ViewHolder(view)
    }

    // Une los datos de la canción con la fila correspondiente.
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cancion = listaCanciones[position]

        // CÁLCULO DE POSICIÓN: Sumamos 4 porque en este adapter la primera fila es realmente el puesto 4.
        val puesto = position + 4
        holder.tvPos.text = "#$puesto"

        // Texto combinado para ahorrar espacio: "Nombre de la canción - Artista"
        holder.tvTitulo.text = "${cancion.titulo} - ${cancion.artista}"

        // --- LÓGICA DINÁMICA: ¿PLAYS O LIKES? ---
        // Dependiendo de lo seleccionado en el rodillo, cambiamos el texto y el color del dato.
        if (esRankingLikes) {
            holder.tvDatos.text = "${cancion.meGusta} ❤"
            holder.tvDatos.setTextColor(Color.parseColor("#E91E63")) // Rosa para Likes
        } else {
            holder.tvDatos.text = "${cancion.visitas} 🎧"
            holder.tvDatos.setTextColor(Color.parseColor("#009688")) // Verde para Plays
        }

        // --- ESTILO VISUAL DINÁMICO ---
        // Accedemos al fondo (GradientDrawable) para cambiar bordes y colores por código.
        val background = holder.layoutFila.background as GradientDrawable

        if (puesto <= 3) {
            // --- CASO ESPECIAL TOP 3 (Si se llegara a usar aquí) ---
            background.setColor(Color.parseColor("#33FFFFFF"))
            background.setStroke(2, if(esRankingLikes) Color.parseColor("#E91E63") else Color.parseColor("#009688"))

            holder.tvPos.setTextColor(Color.WHITE)
            holder.tvTitulo.setTextColor(Color.WHITE)
        } else {
            // --- ESTILO PARA EL RESTO DE LA LISTA (Puesto 4+) ---
            background.setColor(Color.parseColor("#15FFFFFF")) // Fondo casi transparente
            background.setStroke(0, Color.TRANSPARENT) // Sin bordes

            holder.tvPos.setTextColor(Color.parseColor("#B0BEC5")) // Gris para el número
            holder.tvTitulo.setTextColor(Color.parseColor("#E0E0E0")) // Blanco suave para el texto
        }
    }

    // Indica cuántos elementos tiene la lista.
    override fun getItemCount() = listaCanciones.size
}