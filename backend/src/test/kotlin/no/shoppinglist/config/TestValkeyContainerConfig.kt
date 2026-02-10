package no.shoppinglist.config

import no.shoppinglist.service.ValkeyService
import org.testcontainers.containers.GenericContainer

object TestValkeyContainerConfig {
    private val container: GenericContainer<*> =
        GenericContainer("valkey/valkey:8-alpine").apply {
            withExposedPorts(6379)
        }

    private var valkeyService: ValkeyService? = null

    fun init(): ValkeyService {
        if (valkeyService != null) {
            return valkeyService!!
        }

        if (!container.isRunning) {
            container.start()
        }

        val config =
            ValkeyConfig(
                host = container.host,
                port = container.getMappedPort(6379),
                password = "",
            )
        val service = ValkeyService(config)
        service.connect()
        valkeyService = service
        return service
    }
}
