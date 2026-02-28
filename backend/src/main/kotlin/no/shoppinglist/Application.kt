package no.shoppinglist

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.shoppinglist.config.AuthConfig
import no.shoppinglist.config.DatabaseConfig
import no.shoppinglist.config.RecurringConfig
import no.shoppinglist.config.ValkeyConfig
import no.shoppinglist.routes.activityRoutes
import no.shoppinglist.routes.externalRoutes
import no.shoppinglist.routes.asyncApiRoutes
import no.shoppinglist.routes.auth.authRoutes
import no.shoppinglist.routes.comment.householdCommentRoutes
import no.shoppinglist.routes.comment.listCommentRoutes
import no.shoppinglist.routes.household.householdRoutes
import no.shoppinglist.routes.preferencesRoutes
import no.shoppinglist.routes.sharedAccessRoutes
import no.shoppinglist.routes.shoppinglist.shoppingListRoutes
import no.shoppinglist.routes.suggestionRoutes
import no.shoppinglist.routes.webSocketRoutes
import no.shoppinglist.service.ExternalListService
import no.shoppinglist.service.AccountService
import no.shoppinglist.service.ActivityService
import no.shoppinglist.service.CommentService
import no.shoppinglist.service.HouseholdService
import no.shoppinglist.service.ItemHistoryService
import no.shoppinglist.service.JwtService
import no.shoppinglist.service.ListItemService
import no.shoppinglist.service.ListShareService
import no.shoppinglist.service.PinnedListService
import no.shoppinglist.service.PreferencesService
import no.shoppinglist.service.RecurringItemService
import no.shoppinglist.service.RecurringScheduler
import no.shoppinglist.service.RefreshTokenService
import no.shoppinglist.service.ShoppingListService
import no.shoppinglist.service.TokenBlacklistService
import no.shoppinglist.service.ValkeyService
import no.shoppinglist.websocket.EventBroadcaster
import no.shoppinglist.websocket.WebSocketBroadcastService
import no.shoppinglist.websocket.WebSocketSessionManager
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val authConfig = AuthConfig.fromApplicationConfig(environment.config)
    val jwtService = JwtService(authConfig.jwt)
    val valkeyService = ValkeyService(ValkeyConfig.fromApplicationConfig(environment.config))
    valkeyService.connect()
    val tokenBlacklistService = TokenBlacklistService(valkeyService)

    configurePlugins(authConfig, tokenBlacklistService)
    DatabaseConfig.init(environment)

    val services = createServices()
    val refreshTokenService = RefreshTokenService(DatabaseConfig.getDatabase())
    val sessionManager = WebSocketSessionManager()
    val broadcastService = WebSocketBroadcastService(valkeyService, sessionManager)
    val eventBroadcaster = EventBroadcaster(broadcastService)
    val recurringScheduler = createRecurringScheduler(services, eventBroadcaster)

    configureShutdownHooks(refreshTokenService, valkeyService, recurringScheduler)
    configureRouting(
        authConfig,
        jwtService,
        services,
        sessionManager,
        eventBroadcaster,
        refreshTokenService,
        tokenBlacklistService,
        broadcastService,
        valkeyService,
    )
}

private fun Application.createRecurringScheduler(
    services: Services,
    eventBroadcaster: EventBroadcaster,
): RecurringScheduler {
    val recurringConfig = RecurringConfig.fromApplicationConfig(environment.config)
    val scheduler =
        RecurringScheduler(
            config = recurringConfig,
            recurringItemService = services.recurringItemService,
            shoppingListService = services.shoppingListService,
            listItemService = services.listItemService,
            eventBroadcaster = eventBroadcaster,
            db = DatabaseConfig.getDatabase(),
        )
    scheduler.start()
    return scheduler
}

private data class Services(
    val accountService: AccountService,
    val householdService: HouseholdService,
    val shoppingListService: ShoppingListService,
    val listItemService: ListItemService,
    val listShareService: ListShareService,
    val activityService: ActivityService,
    val pinnedListService: PinnedListService,
    val itemHistoryService: ItemHistoryService,
    val preferencesService: PreferencesService,
    val commentService: CommentService,
    val recurringItemService: RecurringItemService,
    val externalListService: ExternalListService,
)

private fun createServices(): Services {
    val db = DatabaseConfig.getDatabase()
    return Services(
        accountService = AccountService(db),
        householdService = HouseholdService(db),
        shoppingListService = ShoppingListService(db),
        listItemService = ListItemService(db),
        listShareService = ListShareService(db),
        activityService = ActivityService(db),
        pinnedListService = PinnedListService(db),
        itemHistoryService = ItemHistoryService(db),
        preferencesService = PreferencesService(db),
        commentService = CommentService(db),
        recurringItemService = RecurringItemService(db),
        externalListService = ExternalListService(db),
    )
}

private fun Application.configurePlugins(
    authConfig: AuthConfig,
    tokenBlacklistService: TokenBlacklistService,
) {
    configureContentNegotiation()
    configureCors()
    configureAuthentication(authConfig, tokenBlacklistService)
    configureWebSockets()
}

private fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 60.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}

private fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            },
        )
    }
}

private fun Application.configureCors() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }
}

private fun Application.configureAuthentication(
    authConfig: AuthConfig,
    tokenBlacklistService: TokenBlacklistService,
) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = authConfig.jwt.realm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(authConfig.jwt.secret))
                    .withIssuer(authConfig.jwt.issuer)
                    .withAudience(authConfig.jwt.audience)
                    .build(),
            )
            validate { credential ->
                if (credential.payload.subject != null) {
                    val jti = credential.payload.id
                    if (jti != null) {
                        val isBlacklisted = runBlocking { tokenBlacklistService.isBlacklisted(jti) }
                        if (isBlacklisted) return@validate null
                    }
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}

private fun Application.configureShutdownHooks(
    refreshTokenService: RefreshTokenService,
    valkeyService: ValkeyService,
    recurringScheduler: RecurringScheduler,
) {
    val cleanupJob =
        CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(6.hours)
                refreshTokenService.cleanupExpired()
            }
        }
    monitor.subscribe(ApplicationStopped) {
        cleanupJob.cancel()
        recurringScheduler.stop()
        valkeyService.shutdown()
    }
}

@Suppress("LongParameterList")
private fun Application.configureRouting(
    authConfig: AuthConfig,
    jwtService: JwtService,
    services: Services,
    sessionManager: WebSocketSessionManager,
    eventBroadcaster: EventBroadcaster,
    refreshTokenService: RefreshTokenService,
    tokenBlacklistService: TokenBlacklistService,
    broadcastService: WebSocketBroadcastService,
    valkeyService: ValkeyService,
) {
    routing {
        route("/api") {
            swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
            get("/health") {
                val valkeyStatus = if (valkeyService.isConnected()) "up" else "degraded"
                call.respondText("OK (valkey: $valkeyStatus)")
            }

            webSocketRoutes(
                authConfig.jwt,
                sessionManager,
                services.shoppingListService,
                services.householdService,
                tokenBlacklistService,
                broadcastService,
            )
            authRoutes(authConfig, services.accountService, jwtService, refreshTokenService, tokenBlacklistService)
            householdRoutes(services.householdService, services.accountService, services.recurringItemService)
            configureListRoutes(services, eventBroadcaster)
            sharedAccessRoutes(services.listShareService, services.listItemService)
            activityRoutes(services.activityService, services.shoppingListService)
            suggestionRoutes(services.itemHistoryService)
            preferencesRoutes(services.preferencesService)
            listCommentRoutes(services.commentService, services.shoppingListService, eventBroadcaster)
            householdCommentRoutes(services.commentService, services.householdService, eventBroadcaster)
            asyncApiRoutes()
        }
        externalRoutes(services.externalListService)
    }
}

private fun io.ktor.server.routing.Route.configureListRoutes(
    services: Services,
    eventBroadcaster: EventBroadcaster,
) {
    shoppingListRoutes(
        services.shoppingListService,
        services.listItemService,
        services.householdService,
        services.listShareService,
        services.pinnedListService,
        eventBroadcaster,
        services.activityService,
        services.itemHistoryService,
        services.recurringItemService,
    )
}
