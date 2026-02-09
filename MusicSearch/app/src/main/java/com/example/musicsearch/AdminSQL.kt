package com.example.musicsearch

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// CAMBIO IMPORTANTE: Versión 3 para añadir nuevos campos
class AdminSQL(context: Context) : SQLiteOpenHelper(context, "musicsearch_db", null, 3) {

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
        insertarDatosIniciales(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Borramos y creamos de nuevo para aplicar cambios limpios
        db.execSQL("DROP TABLE IF EXISTS canciones")
        onCreate(db)
    }

    private fun insertarDatosIniciales(db: SQLiteDatabase) {
        // Formato: titulo, artista, raw, imagen, genero, año, duracion

        // 1. ROBE
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Nada que perder', 'Robe', 'robe_nada_que_perder', 'img_nada_que_perder', 'Rock Progresivo', 2023, '6:15')")

        // 2. WALLS
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Flores mustias', 'Walls', 'walls_flores_mustias', 'img_flores_mustias', 'Pop Rock', 2022, '3:05')")

        // 3. STELLAR
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Ashes', 'Stellar', 'stellar_ashes', 'img_ashes', 'Pop Alternativo', 2020, '2:45')")

        // 4. IMAGINE DRAGONS
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Believer', 'Imagine Dragons', 'id_believer', 'img_believer', 'Pop Rock', 2017, '3:24')")
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Bones', 'Imagine Dragons', 'id_bones', 'img_bones', 'Pop Rock', 2022, '2:45')")
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Demons', 'Imagine Dragons', 'id_demons', 'img_demons', 'Indie Rock', 2012, '2:57')")

        // 5. ESTOPA
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Como Camarón', 'Estopa', 'estopa_como_camaron', 'img_como_camaron', 'Rumba Catalana', 1999, '3:22')")
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Por la raja de tu falda', 'Estopa', 'estopa_raja_falda', 'img_raja_falda', 'Rumba Rock', 1999, '3:25')")
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Vino Tinto', 'Estopa', 'estopa_vino_tinto', 'img_vino_tinto', 'Rumba', 2001, '3:18')")

        // 6. THE LIVING TOMBSTONE
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('My Ordinary Life', 'The Living Tombstone', 'tlt_my_ordinary_life', 'img_my_ordinary_life', 'Electrónica', 2017, '3:52')")
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Discord', 'The Living Tombstone', 'tlt_discord', 'img_discord', 'Eurobeat', 2012, '3:20')")

        // 7. ENRIQUE IGLESIAS
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Bailando', 'Enrique Iglesias', 'ei_bailando', 'img_bailando', 'Pop Latino', 2014, '4:03')")
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Hero', 'Enrique Iglesias', 'ei_hero', 'img_hero', 'Pop Balada', 2001, '4:24')")

        // 8. FITO
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Soldadito Marinero', 'Fito & Fitipaldis', 'fito_soldadito', 'img_soldadito', 'Rock Español', 2003, '3:58')")

        // 9. ALAN WALKER
        db.execSQL("INSERT INTO canciones (titulo, artista, recurso_raw, imagen_uri, genero, anio_lanzamiento, duracion) VALUES ('Faded', 'Alan Walker', 'aw_faded', 'img_faded', 'Electro House', 2015, '3:32')")
    }

    fun sumarVisita(idCancion: Int) {
        val db = this.writableDatabase
        db.execSQL("UPDATE canciones SET visitas = visitas + 1 WHERE id = $idCancion")
        db.close()
    }

    // Método genérico para leer datos y evitar repetir código
    private fun cursorToCancion(cursor: android.database.Cursor): Cancion {
        val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
        val titulo = cursor.getString(cursor.getColumnIndexOrThrow("titulo"))
        val artista = cursor.getString(cursor.getColumnIndexOrThrow("artista"))
        val raw = cursor.getString(cursor.getColumnIndexOrThrow("recurso_raw"))
        val visitas = cursor.getInt(cursor.getColumnIndexOrThrow("visitas"))
        val likes = cursor.getInt(cursor.getColumnIndexOrThrow("megusta"))
        val img = cursor.getString(cursor.getColumnIndexOrThrow("imagen_uri"))

        // Nuevos campos (usamos getColumnIndex con fallback por si acaso, aunque no debería fallar en v3)
        val genero = cursor.getString(cursor.getColumnIndexOrThrow("genero"))
        val anio = cursor.getInt(cursor.getColumnIndexOrThrow("anio_lanzamiento"))
        val duracion = cursor.getString(cursor.getColumnIndexOrThrow("duracion"))

        return Cancion(id, titulo, artista, raw, visitas, likes, img, genero, anio, duracion)
    }

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