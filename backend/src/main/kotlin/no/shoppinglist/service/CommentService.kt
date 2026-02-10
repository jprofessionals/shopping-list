package no.shoppinglist.service

import no.shoppinglist.domain.Comment
import no.shoppinglist.domain.CommentTargetType
import no.shoppinglist.domain.Comments
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class CommentService(
    private val db: Database,
) {
    fun create(
        targetType: CommentTargetType,
        targetId: UUID,
        authorId: UUID,
        text: String,
    ): Comment =
        transaction(db) {
            Comment.new {
                this.targetType = targetType
                this.targetId = targetId
                this.author = no.shoppinglist.domain.Account
                    .findById(authorId)
                    ?: throw IllegalArgumentException("Account not found")
                this.text = text.take(2000)
                this.createdAt = Instant.now()
            }
        }

    fun findByTarget(
        targetType: CommentTargetType,
        targetId: UUID,
        limit: Int = 50,
        offset: Long = 0,
    ): List<Comment> =
        transaction(db) {
            Comment
                .find { (Comments.targetType eq targetType) and (Comments.targetId eq targetId) }
                .orderBy(Comments.createdAt to SortOrder.ASC)
                .limit(limit)
                .offset(offset)
                .toList()
        }

    fun findById(commentId: UUID): Comment? =
        transaction(db) {
            Comment.findById(commentId)
        }

    fun update(
        commentId: UUID,
        accountId: UUID,
        newText: String,
    ): Comment? =
        transaction(db) {
            val comment = Comment.findById(commentId) ?: return@transaction null
            if (comment.author.id.value != accountId) return@transaction null
            comment.text = newText.take(2000)
            comment.editedAt = Instant.now()
            comment
        }

    fun delete(
        commentId: UUID,
        accountId: UUID,
    ): Boolean =
        transaction(db) {
            val comment = Comment.findById(commentId) ?: return@transaction false
            if (comment.author.id.value != accountId) return@transaction false
            comment.delete()
            true
        }

    fun deleteByTarget(
        targetType: CommentTargetType,
        targetId: UUID,
    ): Int =
        transaction(db) {
            val comments =
                Comment.find {
                    (Comments.targetType eq targetType) and (Comments.targetId eq targetId)
                }
            val count = comments.count().toInt()
            comments.forEach { it.delete() }
            count
        }
}
