package com.example.musicsearch

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// AdminSQL: Clase encargada de gestionar la base de datos SQLite del proyecto.
// Hereda de SQLiteOpenHelper para crear la tabla, insertar datos y actualizar versiones.
class AdminSQL(context: Context) : SQLiteOpenHelper(context, "musicsearch_db", null, 3) {

    // Se ejecuta la primera vez que se instala la App: Crea la estructura de la tabla 'canciones'.
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE canciones (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                titulo TEXT,
                artista TEXT,
                recurso_raw TEXT,
                visitas INTEGER DEFAULT 0,
                megusta INTEGER DEFAULT 0,
                imagen_uri TEXT,
                genero TEXT,
                anio_lanzamiento INTEGER,
                duracion TEXT
            )
        """)
        // Una vez creada la tabla, metemos los datos por defecto.
        insertarDatosIniciales(db)
    }

    // Se ejecuta si cambiamos la versión de la DB (ej: de 2 a 3): Borra la tabla vieja y crea la nueva.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS canciones")
        onCreate(db)
    }

    // Carga masiva de canciones para que la App no empiece vacía.
    private fun insertarDatosIniciales(db: SQLiteDatabase) {
        // Robe
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Nada que perder', 'Robe', 'robe_nada_que_perder', 'img_nada_que_perder', 'Rock Progresivo', 2023, '6:15', 4500)")
        // Walls
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Flores mustias', 'Walls', 'walls_flores_mustias', 'img_flores_mustias', 'Pop Rock', 2022, '3:05', 3200)")
        // Stellar
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Ashes', 'Stellar', 'stellar_ashes', 'img_ashes', 'Pop Alternativo', 2020, '2:45', 4100)")
        // Imagine Dragons
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Believer', 'Imagine Dragons', 'id_believer', 'img_believer', 'Pop Rock', 2017, '3:24', 15400)")
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Bones', 'Imagine Dragons', 'id_bones', 'img_bones', 'Pop Rock', 2022, '2:45', 12500)")
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Demons', 'Imagine Dragons', 'id_demons', 'img_demons', 'Indie Rock', 2012, '2:57', 18000)")
        // Estopa
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Como Camarón', 'Estopa', 'estopa_como_camaron', 'img_como_camaron', 'Rumba Catalana', 1999, '3:22', 8900)")
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Por la raja de tu falda', 'Estopa', 'estopa_raja_falda', 'img_raja_falda', 'Rumba Rock', 1999, '3:25', 9500)")
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Vino Tinto', 'Estopa', 'estopa_vino_tinto', 'img_vino_tinto', 'Rumba', 2001, '3:18', 8200)")
        // The Living Tombstone
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('My Ordinary Life', 'The Living Tombstone', 'tlt_my_ordinary_life', 'img_my_ordinary_life', 'Electrónica', 2017, '3:52', 6700)")
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Discord', 'The Living Tombstone', 'tlt_discord', 'img_discord', 'Eurobeat', 2012, '3:20', 5600)")
        // Enrique Iglesias
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Bailando', 'Enrique Iglesias', 'ei_bailando', 'img_bailando', 'Pop Latino', 2014, '4:03', 31000)")
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Hero', 'Enrique Iglesias', 'ei_hero', 'img_hero', 'Pop Balada', 2001, '4:24', 7200)")
        // Fito
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Soldadito Marinero', 'Fito & Fitipaldis', 'fito_soldadito', 'img_soldadito', 'Rock Español', 2003, '3:58', 11000)")
        // Alan Walker
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion, megusta) VALUES ('Faded', 'Alan Walker', 'aw_faded', 'img_faded', 'Electro House', 2015, '3:32', 23000)")
    }

    // Actualiza el contador de visitas (+1) de una canción específica en la DB.
    fun sumarVisita(idCancion: Int) {
        val db = this.writableDatabase // Abrimos modo escritura
        db.execSQL("UPDATE canciones SET visitas = visitas + 1 WHERE id = $idCancion")
        db.close()
    }

    // Helper: Convierte la fila actual del Cursor (resultado SQL) en un objeto Cancion de Kotlin.
    private fun cursorToCancion(cursor: android.database.Cursor): Cancion {
        // Obtenemos los datos por el nombre de la columna para evitar errores de posición.
        val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
        val titulo = cursor.getString(cursor.getColumnIndexOrThrow("titulo"))
        val artista = cursor.getString(cursor.getColumnIndexOrThrow("artista"))
        val raw = cursor.getString(cursor.getColumnIndexOrThrow("recurso_raw"))
        val visitas = cursor.getInt(cursor.getColumnIndexOrThrow("visitas"))
        val likes = cursor.getInt(cursor.getColumnIndexOrThrow("megusta"))
        val img = cursor.getString(cursor.getColumnIndexOrThrow("imagen_uri"))
        val genero = cursor.getString(cursor.getColumnIndexOrThrow("genero"))
        val anio = cursor.getInt(cursor.getColumnIndexOrThrow("anio_lanzamiento"))
        val duracion = cursor.getString(cursor.getColumnIndexOrThrow("duracion"))

        return Cancion(id, titulo, artista, raw, visitas, likes, img, genero, anio, duracion)
    }

    // Devuelve todas las canciones de la tabla ordenadas alfabéticamente por título.
    fun obtenerTodasLasCanciones(): ArrayList<Cancion> {
        val lista = ArrayList<Cancion>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM canciones ORDER BY titulo ASC", null)
        if (cursor.moveToFirst()) {
            do { lista.add(cursorToCancion(cursor)) } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return lista
    }

    // Consulta el Ranking de las 10 canciones con más visitas.
    fun obtenerTopCanciones(): ArrayList<Cancion> {
        val lista = ArrayList<Cancion>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM canciones ORDER BY visitas DESC LIMIT 10", null)
        if (cursor.moveToFirst()) {
            do { lista.add(cursorToCancion(cursor)) } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return lista
    }

    // Consulta el Ranking de las 10 canciones con más "Me gusta".
    fun obtenerTopLikes(): ArrayList<Cancion> {
        val lista = ArrayList<Cancion>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM canciones ORDER BY megusta DESC LIMIT 10", null)
        if (cursor.moveToFirst()) {
            do { lista.add(cursorToCancion(cursor)) } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return lista
    }

    // Busca una canción concreta por su ID (usado en la pantalla de Detalle).
    fun obtenerCancionPorId(id: Int): Cancion? {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM canciones WHERE id = ?", arrayOf(id.toString()))
        var cancion: Cancion? = null
        if (cursor.moveToFirst()) {
            cancion = cursorToCancion(cursor)
        }
        cursor.close()
        db.close()
        return cancion
    }
}