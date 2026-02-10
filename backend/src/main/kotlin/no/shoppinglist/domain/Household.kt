package no.shoppinglist.domain

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object Households : UUIDTable("households") {
    val name = varchar("name", 255)
    val createdAt = timestamp("created_at")
}

class Household(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Household>(Households)

    var name by Households.name
    var createdAt by Households.createdAt
}

enum class MembershipRole {
    OWNER,
    MEMBER,
}

object HouseholdMemberships : UUIDTable("household_memberships") {
    val account = reference("account_id", Accounts)
    val household = reference("household_id", Households)
    val role = enumerationByName<MembershipRole>("role", 20)
    val joinedAt = timestamp("joined_at")

    init {
        uniqueIndex(account, household)
    }
}

class HouseholdMembership(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<HouseholdMembership>(HouseholdMemberships)

    var account by Account referencedOn HouseholdMemberships.account
    var household by Household referencedOn HouseholdMemberships.household
    var role by HouseholdMemberships.role
    var joinedAt by HouseholdMemberships.joinedAt
}
