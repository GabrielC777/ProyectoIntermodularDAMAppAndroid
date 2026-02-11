package com.example.musicsearch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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

// MusicaService: Servicio que gestiona la reproducción de audio en segundo plano.
// Mantiene la música sonando aunque el usuario salga de la App y controla la notificación multimedia.
class MusicaService : Service() {

    // =========================================================
    //  VARIABLES GLOBALES Y REPRODUCTOR
    // =========================================================
    // El motor de audio de Android.
    private var mediaPlayer: MediaPlayer? = null
    // Nombre del archivo en la carpeta res/raw (ej: "cancion_01").
    private var nombreCancionActual: String? = null
    // ID numérico del recurso para cargarlo en el MediaPlayer.
    private var idCancionActual: Int = 0

    // COLA Y HISTORIAL: Listas de IDs para gestionar el orden de reproducción.
    private val colaReproduccion = ArrayList<Int>()
    private val historialReproduccion = ArrayList<Int>()
    private val CAPACIDAD_MAXIMA = 50

    // COMPONENTES DE SESIÓN Y NOTIFICACIÓN: Permiten el control externo (pantalla bloqueo, cascos).
    private lateinit var mediaSession: MediaSessionCompat
    private val CHANNEL_ID = "canal_musica_pokedex"
    private val NOTIFICATION_ID = 1

    // Binder: El "cable" que permite a las Actividades conectarse a este servicio.
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): MusicaService = this@MusicaService
    }

    // =========================================================
    //  INICIALIZACIÓN DEL SERVICIO
    // =========================================================
    override fun onCreate() {
        super.onCreate()

        // Configuración de la MediaSession para control remoto y botones físicos.
        mediaSession = MediaSessionCompat(this, "MusicaService")
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        // Callback para capturar los eventos de los botones multimedia del sistema.
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { reanudarMusica() }
            override fun onPause() { pausarMusica() }
            override fun onSkipToNext() { playNext() }
            override fun onSkipToPrevious() { playPrev() }
            override fun onStop() { stopService() }
            // Sincroniza el slider de la notificación con la posición del audio.
            override fun onSeekTo(pos: Long) {
                seekTo(pos.toInt())
            }
        })

        crearCanalNotificacion()
    }

    // Gestiona los comandos enviados mediante Intents desde la App o la Notificación.
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
        // START_NOT_STICKY: El servicio no se reiniciará automáticamente si el sistema lo mata.
        return START_NOT_STICKY
    }

    // =========================================================
    //  LÓGICA DE REPRODUCCIÓN INTERNA
    // =========================================================

    // Prepara la transición a una canción nueva gestionando el historial.
    private fun reproducirNuevaCancion(resId: Int) {
        if (idCancionActual != 0 && idCancionActual != resId) {
            historialReproduccion.add(idCancionActual)
        }
        playSongInternal(resId)
    }

    // El motor: Libera el audio anterior, carga el nuevo recurso y comienza el play.
    private fun playSongInternal(resId: Int) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()

            idCancionActual = resId
            nombreCancionActual = resources.getResourceEntryName(resId)

            mediaPlayer = MediaPlayer.create(this, resId)

            // Listener: Cuando una canción termina, salta a la siguiente de la cola.
            mediaPlayer?.setOnCompletionListener {
                playNext()
            }

            mediaPlayer?.start()

            // Actualización de UI, notificación y metadatos del sistema.
            actualizarMetadatos(resId)
            actualizarEstadoReproduccion(PlaybackStateCompat.STATE_PLAYING)
            startForeground(NOTIFICATION_ID, crearNotificacion(true))
            notificarCambioUI()

        } catch (e: Exception) { e.printStackTrace() }
    }

    // Lógica para avanzar en la lista: saca de la cola y reproduce.
    fun playNext() {
        if (colaReproduccion.isNotEmpty()) {
            val siguienteId = colaReproduccion.removeAt(0)
            if (idCancionActual != 0) historialReproduccion.add(idCancionActual)
            playSongInternal(siguienteId)
        } else {
            // Si no hay más música, pausamos el reproductor y permitimos cerrar la notificación.
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
            mediaPlayer?.seekTo(0)

            actualizarEstadoReproduccion(PlaybackStateCompat.STATE_PAUSED)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, crearNotificacion(false))
            stopForeground(false) // Permite quitar la notificación deslizando.

            notificarCambioUI()
        }
    }

    // Lógica para retroceder: reinicia la pista o busca en el historial.
    fun playPrev() {
        // Si la canción lleva más de 3 segundos, la reiniciamos.
        if (mediaPlayer != null && mediaPlayer!!.currentPosition > 3000) {
            seekTo(0)
        } else if (historialReproduccion.isNotEmpty()) {
            // Sacamos la última del historial y la cargamos.
            val anteriorId = historialReproduccion.removeAt(historialReproduccion.size - 1)
            if (idCancionActual != 0) colaReproduccion.add(0, idCancionActual)
            playSongInternal(anteriorId)
        } else {
            seekTo(0)
        }

        // Refrescamos el estado visual de la notificación.
        val estaSonando = isPlaying()
        actualizarEstadoReproduccion(if (estaSonando) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, crearNotificacion(estaSonando))
    }

    // Pausa el audio y actualiza la UI externa (notificación).
    private fun pausarMusica() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            actualizarEstadoReproduccion(PlaybackStateCompat.STATE_PAUSED)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, crearNotificacion(false))
            stopForeground(false)
            notificarCambioUI()
        }
    }

    // Reanuda el audio y vuelve a poner el servicio en primer plano.
    private fun reanudarMusica() {
        if (mediaPlayer != null) {
            mediaPlayer?.start()
            actualizarEstadoReproduccion(PlaybackStateCompat.STATE_PLAYING)
            startForeground(NOTIFICATION_ID, crearNotificacion(true))
            notificarCambioUI()
        }
    }

    // Detención total y cierre del servicio.
    private fun stopService() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
        }
        stopForeground(true)
        stopSelf()
    }

    // =========================================================
    //  SINCRONIZACIÓN DE POSICIÓN Y SLIDER
    // =========================================================

    // Mueve el punto de reproducción y avisa a la MediaSession para sincronizar el sistema.
    fun seekTo(pos: Int) {
        mediaPlayer?.seekTo(pos)

        // Al mover el slider manualmente, debemos avisar a la MediaSession
        // de la nueva posición y el tiempo exacto, para que la barra de notificaciones se sincronice.
        val estado = if (isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        actualizarEstadoReproduccion(estado)
    }

    // Informa al sistema qué botones mostrar (Play, Next, etc.) y en qué segundo va la música.
    private fun actualizarEstadoReproduccion(estado: Int) {
        val posicion = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val velocidad = if (estado == PlaybackStateCompat.STATE_PLAYING) 1f else 0f

        // Definimos las acciones disponibles basándonos en si hay cola o historial.
        var acciones = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO

        if (colaReproduccion.isNotEmpty()) {
            acciones = acciones or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }
        if (historialReproduccion.isNotEmpty() || (mediaPlayer?.currentPosition ?: 0) > 3000) {
            acciones = acciones or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }

        // SystemClock.elapsedRealtime() es CLAVE para que la barra de notificación se mueva suavemente.
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(acciones)
            .setState(estado, posicion, velocidad, SystemClock.elapsedRealtime())
            .build())
    }

    // =========================================================
    //  CONSTRUCCIÓN DE NOTIFICACIÓN Y METADATOS
    // =========================================================

    // Genera la notificación multimedia con botones dinámicos y arte del álbum.
    private fun crearNotificacion(isPlaying: Boolean): Notification {
        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata?.description

        // PendingIntents: Acciones diferidas para cuando se pulsan los botones de la notificación.
        val pPrev = PendingIntent.getService(this, 1, Intent(this, MusicaService::class.java).apply { action = "ACCION_PREV" }, PendingIntent.FLAG_IMMUTABLE)
        val pNext = PendingIntent.getService(this, 2, Intent(this, MusicaService::class.java).apply { action = "ACCION_NEXT" }, PendingIntent.FLAG_IMMUTABLE)

        // El icono y la acción cambian dinámicamente según si la música está sonando.
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

        // Botones fijos de la notificación.
        builder.addAction(android.R.drawable.ic_media_previous, "Prev", pPrev)
        builder.addAction(iconPlayPause, "Play", pPlayPause)
        builder.addAction(android.R.drawable.ic_media_next, "Next", pNext)

        // MediaStyle: Aplica el diseño compacto y multimedia estándar de Android.
        builder.setStyle(MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
        )

        return builder.build()
    }

    // Recupera la información de la canción (título, artista, imagen) y la carga en la sesión multimedia.
    private fun actualizarMetadatos(resId: Int) {
        val db = AdminSQL(this)
        val nombreRaw = resources.getResourceEntryName(resId)
        val cancion = db.obtenerTodasLasCanciones().find { it.recursoRaw == nombreRaw }

        val titulo = cancion?.titulo ?: "Reproduciendo"
        val artista = cancion?.artista ?: "Desconocido"
        var bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground)

        // Intentamos cargar la imagen desde los recursos drawable.
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

    // Canal obligatorio para Android 8.0+ para poder mostrar la notificación.
    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "Reproductor Música", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
    }

    // Envía un aviso por Broadcast para que el MiniPlayer de las actividades se refresque.
    private fun notificarCambioUI() {
        sendBroadcast(Intent("EVENTO_ACTUALIZAR_MINIPLAYER").setPackage(packageName))
    }

    // Gestión de la cola de espera: añade un ID y refresca la interfaz.
    fun agregarACola(resId: Int): Boolean {
        if (colaReproduccion.size >= CAPACIDAD_MAXIMA) return false
        colaReproduccion.add(resId)

        // Actualizamos la notificación para que el sistema sepa si el botón "Next" debe habilitarse.
        val estado = if (isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        actualizarEstadoReproduccion(estado)

        if (isPlaying()) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, crearNotificacion(true))
        }
        notificarCambioUI()
        return true
    }

    // =========================================================
    //  MÉTODOS DE ACCESO PARA LA UI (GETTERS)
    // =========================================================
    fun getCola(): List<Int> = colaReproduccion
    fun hasHistory(): Boolean = historialReproduccion.isNotEmpty()
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
    fun getNombreCancion(): String? = nombreCancionActual
    fun getPosicionActual(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuracionTotal(): Int = mediaPlayer?.duration ?: 0

    // Lifecycle Binder: gestiona la conexión Activity-Servicio.
    override fun onBind(intent: Intent?): IBinder = binder

    // Limpieza de recursos al cerrar el servicio.
    override fun onDestroy() {
        mediaSession.release()
        mediaPlayer?.release()
        super.onDestroy()
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Este método se dispara ÚNICAMENTE cuando el usuario desliza la app
        // hacia fuera en el menú de "Apps Recientes".

        // 1. Detenemos el reproductor
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
        }

        // 2. Quitamos la notificación y el estado de prioridad
        stopForeground(true)

        // 3. Matamos el servicio por completo
        stopSelf()

        super.onTaskRemoved(rootIntent)
    }
}