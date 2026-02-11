package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.Household
import no.shoppinglist.domain.RecurringFrequency
import no.shoppinglist.domain.RecurringItem
import no.shoppinglist.domain.RecurringItems
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID

class RecurringItemService(
    private val db: Database,
) {
    fun getByHousehold(householdId: UUID): List<RecurringItem> =
        transaction(db) {
            RecurringItem
                .find { RecurringItems.household eq householdId }
                .toList()
        }

    fun findById(id: UUID): RecurringItem? =
        transaction(db) {
            RecurringItem.findById(id)
        }

    fun create(
        householdId: UUID,
        accountId: UUID,
        name: String,
        quantity: Double,
        unit: String?,
        frequency: RecurringFrequency,
    ): RecurringItem =
        transaction(db) {
            val household =
                Household.findById(householdId)
                    ?: throw IllegalArgumentException("Household not found: $householdId")
            val account =
                Account.findById(accountId)
                    ?: throw IllegalArgumentException("Account not found: $accountId")

            RecurringItem.new {
                this.household = household
                this.name = name
                this.quantity = quantity
                this.unit = unit
                this.frequency = frequency
                this.lastPurchased = null
                this.isActive = true
                this.pausedUntil = null
                this.createdBy = account
            }
        }

    fun update(
        id: UUID,
        name: String,
        quantity: Double,
        unit: String?,
        frequency: RecurringFrequency,
    ): RecurringItem? =
        transaction(db) {
            val item = RecurringItem.findById(id) ?: return@transaction null
            item.name = name
            item.quantity = quantity
            item.unit = unit
            item.frequency = frequency
            item
        }

    fun delete(id: UUID): Boolean =
        transaction(db) {
            val deleted = RecurringItems.deleteWhere { RecurringItems.id eq id }
            deleted > 0
        }

    fun pause(
        id: UUID,
        until: LocalDate?,
    ): RecurringItem? =
        transaction(db) {
            val item = RecurringItem.findById(id) ?: return@transaction null
            item.isActive = false
            item.pausedUntil = until
            item
        }

    fun resume(id: UUID): RecurringItem? =
        transaction(db) {
            val item = RecurringItem.findById(id) ?: return@transaction null
            item.isActive = true
            item.pausedUntil = null
            item
        }

    fun markPurchased(
        id: UUID,
        date: LocalDate = LocalDate.now(),
    ): RecurringItem? =
        transaction(db) {
            val item = RecurringItem.findById(id) ?: return@transaction null
            item.lastPurchased = date
            item
        }

    fun findActiveByHousehold(householdId: UUID): List<RecurringItem> =
        transaction(db) {
            RecurringItem
                .find {
                    (RecurringItems.household eq householdId) and
                        (RecurringItems.isActive eq true)
                }.toList()
        }

    fun reactivateExpiredPauses(today: LocalDate = LocalDate.now()): Int =
        transaction(db) {
            val expired =
                RecurringItem
                    .find {
                        (RecurringItems.isActive eq false) and
                            (RecurringItems.pausedUntil lessEq today)
                    }.toList()

            expired.forEach { item ->
                item.isActive = true
                item.pausedUntil = null
            }
            expired.size
        }
}
