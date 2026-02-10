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
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class MusicaService : Service() {

    // --- VARIABLES GLOBALES ---
    private var mediaPlayer: MediaPlayer? = null
    private var nombreCancionActual: String? = null
    private var idCancionActual: Int = 0

    // COLA Y HISTORIAL
    private val colaReproduccion = ArrayList<Int>()
    private val historialReproduccion = ArrayList<Int>()
    private val CAPACIDAD_MAXIMA = 50

    // COMPONENTES
    private lateinit var mediaSession: MediaSessionCompat
    private val CHANNEL_ID = "canal_musica_pokedex"
    private val NOTIFICATION_ID = 1

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): MusicaService = this@MusicaService
    }

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "MusicaService")
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { reanudarMusica() }
            override fun onPause() { pausarMusica() }
            override fun onSkipToNext() { playNext() }
            override fun onSkipToPrevious() { playPrev() }
            override fun onStop() { stopService() }
            // ESTO PERMITE QUE EL SLIDER DE LA NOTIFICACIÓN CONTROLE LA APP
            override fun onSeekTo(pos: Long) {
                seekTo(pos.toInt())
            }
        })

        crearCanalNotificacion()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val accion = intent?.action
        when (accion) {
            "CAMBIAR_CANCION" -> {
                val id = intent.getIntExtra("ID_CANCION", 0)
                if (id != 0) reproducirNuevaCancion(id)
            }
            "ACCION_PLAY", "RESUME" -> reanudarMusica()
            "ACCION_PAUSE", "PAUSE" -> pausarMusica()
            "ACCION_NEXT", "ACTION_NEXT" -> playNext()
            "ACCION_PREV", "ACTION_PREV" -> playPrev()
            "AGREGAR_COLA" -> {
                val id = intent.getIntExtra("ID_CANCION", 0)
                if (id != 0) agregarACola(id)
            }
            "STOP" -> stopService()
        }
        return START_NOT_STICKY
    }

    // --- LÓGICA DE REPRODUCCIÓN ---

    private fun reproducirNuevaCancion(resId: Int) {
        if (idCancionActual != 0 && idCancionActual != resId) {
            historialReproduccion.add(idCancionActual)
        }
        playSongInternal(resId)
    }

    private fun playSongInternal(resId: Int) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()

            idCancionActual = resId
            nombreCancionActual = resources.getResourceEntryName(resId)

            mediaPlayer = MediaPlayer.create(this, resId)

            // Cuando la canción termina...
            mediaPlayer?.setOnCompletionListener {
                playNext()
            }

            mediaPlayer?.start()

            actualizarMetadatos(resId)
            actualizarEstadoReproduccion(PlaybackStateCompat.STATE_PLAYING)
            startForeground(NOTIFICATION_ID, crearNotificacion(true))
            notificarCambioUI()

        } catch (e: Exception) { e.printStackTrace() }
    }

    fun playNext() {
        if (colaReproduccion.isNotEmpty()) {
            val siguienteId = colaReproduccion.removeAt(0)
            if (idCancionActual != 0) historialReproduccion.add(idCancionActual)
            playSongInternal(siguienteId)
        } else {
            // --- CORRECCIÓN: FIN DE LA COLA ---
            // 1. Pausamos el audio
            // 2. Rebobinamos al principio (opcional, pero queda mejor)
            // 3. Actualizamos la notificación a estado "PAUSA" (Botón Play visible)
            // 4. Permitimos quitar la notificación (stopForeground false)

            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
            mediaPlayer?.seekTo(0)

            actualizarEstadoReproduccion(PlaybackStateCompat.STATE_PAUSED)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, crearNotificacion(false)) // false = mostrar icono Play
            stopForeground(false)

            notificarCambioUI()
        }
    }

    fun playPrev() {
        if (mediaPlayer != null && mediaPlayer!!.currentPosition > 3000) {
            seekTo(0)
        } else if (historialReproduccion.isNotEmpty()) {
            val anteriorId = historialReproduccion.removeAt(historialReproduccion.size - 1)
            if (idCancionActual != 0) colaReproduccion.add(0, idCancionActual)
            playSongInternal(anteriorId)
        } else {
            seekTo(0) // Si no hay historial, reinicia
        }

        // Refrescamos notificación por si cambia estado de botones
        val estaSonando = isPlaying()
        actualizarEstadoReproduccion(if (estaSonando) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, crearNotificacion(estaSonando))
    }

    private fun pausarMusica() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            actualizarEstadoReproduccion(PlaybackStateCompat.STATE_PAUSED)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, crearNotificacion(false))
            stopForeground(false)
            notificarCambioUI()
        }
    }

    private fun reanudarMusica() {
        if (mediaPlayer != null) {
            mediaPlayer?.start()
            actualizarEstadoReproduccion(PlaybackStateCompat.STATE_PLAYING)
            startForeground(NOTIFICATION_ID, crearNotificacion(true))
            notificarCambioUI()
        }
    }

    private fun stopService() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
        }
        stopForeground(true)
        stopSelf()
    }

    // --- SINCRONIZACIÓN DE SLIDER ---

    // Este método se llama desde el MiniPlayer (MusicBaseActivity)
    fun seekTo(pos: Int) {
        mediaPlayer?.seekTo(pos)

        // IMPORTANTE: Al mover el slider manualmente, debemos avisar a la MediaSession
        // de la nueva posición y el tiempo exacto, para que la barra de notificaciones se sincronice.
        val estado = if (isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        actualizarEstadoReproduccion(estado)
    }

    private fun actualizarEstadoReproduccion(estado: Int) {
        val posicion = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val velocidad = if (estado == PlaybackStateCompat.STATE_PLAYING) 1f else 0f

        var acciones = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO

        if (colaReproduccion.isNotEmpty()) {
            acciones = acciones or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }
        if (historialReproduccion.isNotEmpty() || (mediaPlayer?.currentPosition ?: 0) > 3000) {
            acciones = acciones or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }

        // SystemClock.elapsedRealtime() es CLAVE para que la barra de notificación se mueva suavemente
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(acciones)
            .setState(estado, posicion, velocidad, SystemClock.elapsedRealtime())
            .build())
    }

    // --- CONSTRUCCIÓN DE NOTIFICACIÓN ---

    private fun crearNotificacion(isPlaying: Boolean): Notification {
        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata?.description

        val pPrev = PendingIntent.getService(this, 1, Intent(this, MusicaService::class.java).apply { action = "ACCION_PREV" }, PendingIntent.FLAG_IMMUTABLE)
        val pNext = PendingIntent.getService(this, 2, Intent(this, MusicaService::class.java).apply { action = "ACCION_NEXT" }, PendingIntent.FLAG_IMMUTABLE)

        // El icono y la acción cambian según si está sonando o no
        val iconPlayPause = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val accionPlayPause = if (isPlaying) "ACCION_PAUSE" else "ACCION_PLAY"

        val pPlayPause = PendingIntent.getService(this, 0, Intent(this, MusicaService::class.java).apply { action = accionPlayPause }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(description?.title ?: "Música")
            .setContentText(description?.subtitle)
            .setLargeIcon(description?.iconBitmap)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pApp)
            .setDeleteIntent(PendingIntent.getService(this, 99, Intent(this, MusicaService::class.java).apply { action = "STOP" }, PendingIntent.FLAG_IMMUTABLE))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Botones fijos: Prev - Play/Pause - Next
        builder.addAction(android.R.drawable.ic_media_previous, "Prev", pPrev)
        builder.addAction(iconPlayPause, "Play", pPlayPause)
        builder.addAction(android.R.drawable.ic_media_next, "Next", pNext)

        builder.setStyle(MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
        )

        return builder.build()
    }

    private fun actualizarMetadatos(resId: Int) {
        val db = AdminSQL(this)
        val nombreRaw = resources.getResourceEntryName(resId)
        val cancion = db.obtenerTodasLasCanciones().find { it.recursoRaw == nombreRaw }

        val titulo = cancion?.titulo ?: "Reproduciendo"
        val artista = cancion?.artista ?: "Desconocido"
        var bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground)

        cancion?.let {
            val imgId = resources.getIdentifier(it.imagenUri, "drawable", packageName)
            if (imgId != 0) bitmap = BitmapFactory.decodeResource(resources, imgId)
        }

        mediaSession.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titulo)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artista)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer?.duration?.toLong() ?: 0L)
            .build())
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "Reproductor Música", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
    }

    private fun notificarCambioUI() {
        sendBroadcast(Intent("EVENTO_ACTUALIZAR_MINIPLAYER").setPackage(packageName))
    }

    fun agregarACola(resId: Int): Boolean {
        if (colaReproduccion.size >= CAPACIDAD_MAXIMA) return false
        colaReproduccion.add(resId)

        // Actualizar notificación para habilitar botón Next
        val estado = if (isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        actualizarEstadoReproduccion(estado)

        if (isPlaying()) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, crearNotificacion(true))
        }
        notificarCambioUI()
        return true
    }

    fun getCola(): List<Int> = colaReproduccion
    fun hasHistory(): Boolean = historialReproduccion.isNotEmpty()
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
    fun getNombreCancion(): String? = nombreCancionActual
    fun getPosicionActual(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuracionTotal(): Int = mediaPlayer?.duration ?: 0

    override fun onBind(intent: Intent?): IBinder = binder
    override fun onDestroy() {
        mediaSession.release()
        mediaPlayer?.release()
        super.onDestroy()
    }
}