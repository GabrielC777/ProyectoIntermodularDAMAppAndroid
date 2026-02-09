package com.example.musicsearch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class MusicaService : Service() {

    // MÉTODO AUXILIAR PARA AVISAR A LAS PANTALLAS
    private fun notificarCambioUI() {
        val intent = Intent("EVENTO_ACTUALIZAR_MINIPLAYER")
        intent.setPackage(packageName) // <--- ESTO ES LA CLAVE DE LA SEGURIDAD
        sendBroadcast(intent)
    }

    private var mediaPlayer: MediaPlayer? = null
    private var nombreCancionActual: String? = null

    // VARIABLES PARA LA NOTIFICACIÓN PRO
    private lateinit var mediaSession: MediaSessionCompat
    private val CHANNEL_ID = "canal_musica_pokedex"
    private val NOTIFICATION_ID = 1

    // Binder para conectar con activities
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): MusicaService = this@MusicaService
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Crear la Sesión Multimedia (El cerebro de la notificación)
        mediaSession = MediaSessionCompat(this, "MusicaService")
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        // Callback: Qué hacer cuando tocas los botones de la notificación
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { reanudarMusica() }
            override fun onPause() { pausarMusica() }
            override fun onStop() {
                stopForeground(true)
                stopSelf()
            }
            override fun onSeekTo(pos: Long) {
                mediaPlayer?.seekTo(pos.toInt())
                actualizarEstadoReproduccion(PlaybackStateCompat.STATE_PLAYING)
            }
        })

        crearCanalNotificacion()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val accion = intent?.action

        // Manejo de botones de la notificación (PendingIntents)
        when (accion) {
            "ACCION_PLAY" -> reanudarMusica()
            "ACCION_PAUSE" -> pausarMusica()
            "CAMBIAR_CANCION" -> {
                val idSolicitado = intent.getIntExtra("ID_CANCION", 0)
                if (idSolicitado != 0) reproducirCancionPorId(idSolicitado)
            }
            "PAUSE" -> pausarMusica() // Llamadas desde la Activity
            "RESUME" -> reanudarMusica()
        }

        return START_NOT_STICKY
    }

    private fun reproducirCancionPorId(resId: Int) {
        try {
            val nombreRaw = resources.getResourceEntryName(resId)

            if (nombreRaw == nombreCancionActual && mediaPlayer != null) {
                if (!mediaPlayer!!.isPlaying) reanudarMusica()
                return
            }

            mediaPlayer?.stop()
            mediaPlayer?.release()

            mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()

            nombreCancionActual = nombreRaw

            // 1. Actualizamos todos los datos internos y la notificación
            // Actualizamos notificación
            actualizarMetadatos(resId)
            actualizarEstadoReproduccion(PlaybackStateCompat.STATE_PLAYING)
            startForeground(NOTIFICATION_ID, crearNotificacion(true))

            // AVISAMOS A LA UI AL FINAL (Cuando ya todo está listo)
            notificarCambioUI()

        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun pausarMusica() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()

            // --- NUEVO: AVISAR AL MINIPLAYER ---
            notificarCambioUI()

            actualizarEstadoReproduccion(PlaybackStateCompat.STATE_PAUSED)
            // Actualizar notifiación (quitar botón pause, poner play)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, crearNotificacion(false))
            // IMPORTANTE: stopForeground(false) mantiene la noti pero permite quitarla si deslizas
            stopForeground(false)
        }
    }

    private fun reanudarMusica() {
        if (mediaPlayer != null) {
            mediaPlayer?.start()
            notificarCambioUI()
            actualizarEstadoReproduccion(PlaybackStateCompat.STATE_PLAYING)
            startForeground(NOTIFICATION_ID, crearNotificacion(true))
        }
    }

    // --- MÉTODOS DE MEDIA SESSION ---

    private fun actualizarMetadatos(resId: Int) {
        // Obtenemos datos de la DB para que salgan bonitos en la notificación
        val db = AdminSQL(this)
        val nombreRaw = resources.getResourceEntryName(resId)
        val todas = db.obtenerTodasLasCanciones()
        val cancion = todas.find { it.recursoRaw == nombreRaw }

        val titulo = cancion?.titulo ?: "Desconocido"
        val artista = cancion?.artista ?: "Artista"

        // Intentamos cargar la carátula
        var bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground) // Default
        if (cancion != null) {
            val imgId = resources.getIdentifier(cancion.imagenUri, "drawable", packageName)
            if (imgId != 0) {
                bitmap = BitmapFactory.decodeResource(resources, imgId)
            }
        }

        val duracion = mediaPlayer?.duration?.toLong() ?: 0L

        mediaSession.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titulo)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artista)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duracion) // Vital para la barra
            .build())
    }

    private fun actualizarEstadoReproduccion(estado: Int) {
        val posicion = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val velocidad = if (estado == PlaybackStateCompat.STATE_PLAYING) 1f else 0f

        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO)
            .setState(estado, posicion, velocidad)
            .build())
    }

    private fun crearNotificacion(isPlaying: Boolean): Notification {
        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata.description

        // Botón Play/Pause según estado
        val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val accionTexto = if (isPlaying) "Pausar" else "Reproducir"
        val intentAccion = if (isPlaying) "ACCION_PAUSE" else "ACCION_PLAY"

        val pendingIntentBoton = PendingIntent.getService(
            this, 0, Intent(this, MusicaService::class.java).apply { action = intentAccion },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent para abrir la app al tocar la notificación
        val intentApp = Intent(this, MainActivity::class.java)
        val pendingIntentApp = PendingIntent.getActivity(
            this, 0, intentApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(description.title)
            .setContentText(description.subtitle)
            .setSubText(description.description)
            .setLargeIcon(description.iconBitmap)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Pon aquí un icono pequeño de música si tienes
            .setContentIntent(pendingIntentApp)
            .setDeleteIntent(PendingIntent.getService(this, 0, Intent(this, MusicaService::class.java).apply { action = "STOP" }, PendingIntent.FLAG_IMMUTABLE))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            // ESTO CREA EL ESTILO MEDIA
            .setStyle(MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0) // Muestra el botón 0 (Play/Pause) en modo compacto
            )

            // Añadir botón Play/Pause
            .addAction(NotificationCompat.Action(icon, accionTexto, pendingIntentBoton))

        return builder.build()
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproductor Música Pokedex",
                NotificationManager.IMPORTANCE_LOW // Low para que no suene pitido cada vez que cambias
            )
            channel.description = "Control de reproducción"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // --- BINDER Y EXTRAS ---
    override fun onBind(intent: Intent?): IBinder { return binder }

    fun isPlaying(): Boolean { return mediaPlayer?.isPlaying == true }
    fun getNombreCancion(): String? { return nombreCancionActual }

    // --- NUEVO: MÉTODOS PARA EL SLIDER (SEEKBAR) ---
    fun getPosicionActual(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuracionTotal(): Int = mediaPlayer?.duration ?: 0

    fun seekTo(posicion: Int) {
        mediaPlayer?.seekTo(posicion)
    }

    override fun onDestroy() {
        mediaSession.release()
        mediaPlayer?.release()
        super.onDestroy()
    }

    // Se ejecuta cuando el usuario borra la app de la lista de recientes (swipe)
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // 1. Parar la notificación (si la tienes activa)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }

        // 2. Destruir el servicio (esto llamará a onDestroy y parará el player)
        stopSelf()
    }
}