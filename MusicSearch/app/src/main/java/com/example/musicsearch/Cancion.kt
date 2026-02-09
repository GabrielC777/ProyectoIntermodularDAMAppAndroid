package com.example.musicsearch

data class Cancion(
    val id: Int,
    val titulo: String,
    val artista: String,
    val recursoRaw: String,
    val visitas: Int,
    val meGusta: Int,
    val imagenUri: String,
    val genero: String = "Desconocido", // Valor por defecto para evitar errores si algo falla
    val anioLanzamiento: Int = 2024,
    val duracion: String = "0:00"
)