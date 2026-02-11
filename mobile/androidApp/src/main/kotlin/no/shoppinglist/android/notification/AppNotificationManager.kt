package no.shoppinglist.android.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import no.shoppinglist.shared.repository.PreferencesRepository
import no.shoppinglist.shared.websocket.NotificationEvent
import no.shoppinglist.shared.websocket.WebSocketEventHandler

class AppNotificationManager(
    private val context: Context,
    private val webSocketEventHandler: WebSocketEventHandler,
    private val preferencesRepository: PreferencesRepository,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var isInBackground = false
    private var notificationId = 1000

    companion object {
        const val CHANNEL_LISTS = "channel_lists"
        const val CHANNEL_ITEMS = "channel_items"
        const val CHANNEL_COMMENTS = "channel_comments"
    }

    fun start() {
        createNotificationChannels()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        observeNotificationEvents()
    }

    fun stop() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        scope.cancel()
    }

    override fun onStart(owner: LifecycleOwner) {
        isInBackground = false
    }

    override fun onStop(owner: LifecycleOwner) {
        isInBackground = true
    }

    private fun createNotificationChannels() {
        val channels = listOf(
            NotificationChannel(CHANNEL_LISTS, "Shopping Lists", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications for new shopping lists"
            },
            NotificationChannel(CHANNEL_ITEMS, "List Items", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications for items added to lists"
            },
            NotificationChannel(CHANNEL_COMMENTS, "Comments", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications for new comments"
            },
        )
        channels.forEach { notificationManager.createNotificationChannel(it) }
    }

    private fun observeNotificationEvents() {
        scope.launch {
            webSocketEventHandler.notificationEvents.collect { event ->
                if (!isInBackground) return@collect
                val prefs = preferencesRepository.preferences.value ?: return@collect

                when (event) {
                    is NotificationEvent.NewList -> {
                        if (prefs.notifyNewList) {
                            showNotification(
                                channelId = CHANNEL_LISTS,
                                title = context.getString(android.R.string.untitled),
                                body = "${event.actorName}: ${event.listName}",
                            )
                        }
                    }
                    is NotificationEvent.ItemAdded -> {
                        if (prefs.notifyItemAdded) {
                            showNotification(
                                channelId = CHANNEL_ITEMS,
                                title = event.listName,
                                body = "${event.actorName}: ${event.itemName}",
                            )
                        }
                    }
                    is NotificationEvent.NewComment -> {
                        if (prefs.notifyNewComment) {
                            showNotification(
                                channelId = CHANNEL_COMMENTS,
                                title = event.authorName,
                                body = event.text,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showNotification(channelId: String, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId++, notification)
    }
}
