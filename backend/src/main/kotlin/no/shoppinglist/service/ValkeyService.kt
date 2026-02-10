package no.shoppinglist.service

import io.lettuce.core.RedisChannelHandler
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionStateListener
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.coroutines.future.await
import no.shoppinglist.config.ValkeyConfig
import org.slf4j.LoggerFactory
import java.time.Duration

@Suppress("TooGenericExceptionCaught")
class ValkeyService(
    config: ValkeyConfig,
) {
    private val logger = LoggerFactory.getLogger(ValkeyService::class.java)

    private val redisUri =
        RedisURI
            .builder()
            .withHost(config.host)
            .withPort(config.port)
            .withTimeout(Duration.ofSeconds(5))
            .apply {
                if (config.password.isNotEmpty()) {
                    withPassword(config.password.toCharArray())
                }
            }.build()

    private val client: RedisClient = RedisClient.create(redisUri)
    private var connection: StatefulRedisConnection<String, String>? = null
    private var pubSubConnection: StatefulRedisPubSubConnection<String, String>? = null
    private val channelListeners = mutableMapOf<String, MutableList<suspend (String, String) -> Unit>>()
    private var connected = false

    fun connect() {
        try {
            connection = client.connect()
            pubSubConnection = client.connectPubSub()
            pubSubConnection?.addListener(
                object : RedisPubSubAdapter<String, String>() {
                    override fun message(
                        channel: String,
                        message: String,
                    ) {
                        val listeners = synchronized(channelListeners) { channelListeners[channel]?.toList() }
                        listeners?.forEach { listener ->
                            kotlinx.coroutines.runBlocking {
                                try {
                                    listener(channel, message)
                                } catch (e: Exception) {
                                    logger.warn("Error in pub/sub listener for channel $channel", e)
                                }
                            }
                        }
                    }
                },
            )
            client.addListener(
                object : RedisConnectionStateListener {
                    override fun onRedisConnected(
                        connection: RedisChannelHandler<*, *>,
                        socketAddress: java.net.SocketAddress,
                    ) {
                        resubscribeAllChannels()
                    }
                },
            )
            connected = true
            logger.info("Connected to Valkey at ${redisUri.host}:${redisUri.port}")
        } catch (e: Exception) {
            logger.warn("Failed to connect to Valkey: ${e.message}. Features will be degraded.")
            connected = false
        }
    }

    fun isConnected(): Boolean = connected && connection?.isOpen == true

    suspend fun set(
        key: String,
        value: String,
        ttlSeconds: Long,
    ): Boolean =
        try {
            if (!isConnected()) return false
            connection?.async()?.setex(key, ttlSeconds, value)?.await()
            true
        } catch (e: Exception) {
            logger.warn("Valkey SET failed for key $key: ${e.message}")
            false
        }

    suspend fun exists(key: String): Boolean =
        try {
            if (!isConnected()) return false
            val result = connection?.async()?.exists(key)?.await() ?: 0L
            result > 0
        } catch (e: Exception) {
            logger.warn("Valkey EXISTS failed for key $key: ${e.message}")
            false
        }

    suspend fun publish(
        channel: String,
        message: String,
    ): Boolean =
        try {
            if (!isConnected()) return false
            connection?.async()?.publish(channel, message)?.await()
            true
        } catch (e: Exception) {
            logger.warn("Valkey PUBLISH failed for channel $channel: ${e.message}")
            false
        }

    fun subscribe(
        channel: String,
        listener: suspend (String, String) -> Unit,
    ) {
        synchronized(channelListeners) {
            channelListeners.computeIfAbsent(channel) { mutableListOf() }.add(listener)
        }
        try {
            if (isConnected()) {
                pubSubConnection?.async()?.subscribe(channel)
            }
        } catch (e: Exception) {
            logger.warn("Valkey SUBSCRIBE failed for channel $channel: ${e.message}")
        }
    }

    fun unsubscribe(channel: String) {
        synchronized(channelListeners) {
            channelListeners.remove(channel)
        }
        try {
            if (isConnected()) {
                pubSubConnection?.async()?.unsubscribe(channel)
            }
        } catch (e: Exception) {
            logger.warn("Valkey UNSUBSCRIBE failed for channel $channel: ${e.message}")
        }
    }

    private fun resubscribeAllChannels() {
        val channels = synchronized(channelListeners) { channelListeners.keys.toList() }
        if (channels.isEmpty()) return
        try {
            logger.info("Re-subscribing to ${channels.size} Pub/Sub channels after reconnect")
            pubSubConnection?.async()?.subscribe(*channels.toTypedArray())
        } catch (e: Exception) {
            logger.warn("Failed to re-subscribe to channels after reconnect: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            pubSubConnection?.close()
            connection?.close()
            client.shutdown()
            connected = false
            logger.info("Valkey connection closed")
        } catch (e: Exception) {
            logger.warn("Error shutting down Valkey: ${e.message}")
        }
    }
}
