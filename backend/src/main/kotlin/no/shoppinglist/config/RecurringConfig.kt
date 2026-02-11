package no.shoppinglist.config

import io.ktor.server.config.ApplicationConfig
import no.shoppinglist.domain.RecurringFrequency
import kotlin.time.Duration

data class RecurringConfig(
    val schedulerInterval: Duration,
    val schedulerInitialDelay: Duration,
    val frequencies: Map<RecurringFrequency, Duration>,
) {
    companion object {
        fun fromApplicationConfig(config: ApplicationConfig): RecurringConfig {
            val recurring = config.config("recurring")
            val scheduler = recurring.config("scheduler")
            val freqs = recurring.config("frequencies")

            return RecurringConfig(
                schedulerInterval = Duration.parse(scheduler.property("interval").getString()),
                schedulerInitialDelay = Duration.parse(scheduler.property("initialDelay").getString()),
                frequencies =
                    mapOf(
                        RecurringFrequency.DAILY to Duration.parse(freqs.property("daily").getString()),
                        RecurringFrequency.WEEKLY to Duration.parse(freqs.property("weekly").getString()),
                        RecurringFrequency.BIWEEKLY to Duration.parse(freqs.property("biweekly").getString()),
                        RecurringFrequency.MONTHLY to Duration.parse(freqs.property("monthly").getString()),
                    ),
            )
        }
    }

    fun getDuration(frequency: RecurringFrequency): Duration =
        frequencies[frequency] ?: error("No duration configured for frequency: $frequency")
}
