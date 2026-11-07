package org.jellyfin.mobile.comments

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * The server/client contract for the Comments plugin, using the payloads documented in
 * `docs/client-api-guide.md`. These are the fields the native sheet relies on.
 */
class CommentModelsTest {

    private val json = Json

    @Test
    fun `parses a comment page with replies and reply-to metadata`() {
        val page = json.decodeFromString<CommentPageDto>(
            """
            {
              "items": [
                {
                  "id": "fc40a184b83c4517b280e4f5585e0404",
                  "itemId": "62bd50f7c9916a91bfd43765e2594df8",
                  "userId": "5ad4d64132c50e8831c828656af05055",
                  "username": "maikel",
                  "avatarUrl": "/Users/5ad4d64132c50e8831c828656af05055/Images/Primary?height=64",
                  "text": "First!",
                  "createdAt": "2026-10-27T09:40:21.6658878Z",
                  "editedAt": null,
                  "parentId": null,
                  "likeCount": 1,
                  "userLiked": false,
                  "isOwn": true,
                  "replyCount": 1,
                  "replies": [
                    {
                      "id": "11111111111111111111111111111111",
                      "itemId": "62bd50f7c9916a91bfd43765e2594df8",
                      "userId": "22222222222222222222222222222222",
                      "username": "them",
                      "text": "me too",
                      "createdAt": "2026-10-27T09:41:21.6658878Z",
                      "parentId": "fc40a184b83c4517b280e4f5585e0404",
                      "replyToCommentId": "fc40a184b83c4517b280e4f5585e0404",
                      "replyToUsername": "maikel",
                      "likeCount": 0,
                      "userLiked": false,
                      "isOwn": false,
                      "replyCount": 0,
                      "replies": []
                    }
                  ]
                }
              ],
              "totalCount": 2,
              "startIndex": 0,
              "hasMore": false,
              "nextCursor": null
            }
            """.trimIndent(),
        )

        page.totalCount shouldBe 2
        page.hasMore shouldBe false
        page.items.single().text shouldBe "First!"
        page.items.single().replies.single().replyToUsername shouldBe "maikel"
        page.items.single().replies.single().parentId shouldBe "fc40a184b83c4517b280e4f5585e0404"
    }

    @Test
    fun `parses a notification page and keeps the server and user switches`() {
        val page = json.decodeFromString<CommentNotificationPageDto>(
            """
            {
              "items": [
                {
                  "id": "33333333333333333333333333333333",
                  "commentId": "11111111111111111111111111111111",
                  "parentCommentId": "fc40a184b83c4517b280e4f5585e0404",
                  "itemId": "62bd50f7c9916a91bfd43765e2594df8",
                  "itemName": "Pilot",
                  "seriesName": "The Show",
                  "itemType": "Episode",
                  "actorId": "22222222222222222222222222222222",
                  "actorName": "them",
                  "actorAvatarUrl": null,
                  "text": "me too",
                  "parentText": "First!",
                  "createdAt": "2026-10-27T09:41:21.6658878Z",
                  "readAt": null,
                  "isRead": false
                }
              ],
              "totalCount": 1,
              "unreadCount": 1,
              "startIndex": 0,
              "hasMore": false,
              "serverEnabled": true,
              "userEnabled": true
            }
            """.trimIndent(),
        )

        val notification = page.items.single()
        notification.itemLabel shouldBe "The Show \u2013 Pilot"
        notification.isRead shouldBe false
        page.unreadCount shouldBe 1
        page.serverEnabled shouldBe true
        page.userEnabled shouldBe true
    }

    @Test
    fun `parses edit and delete permissions and falls back to authorship`() {
        val allowed = json.decodeFromString<CommentDto>(
            """
            {
              "id": "fc40a184b83c4517b280e4f5585e0404",
              "itemId": "62bd50f7c9916a91bfd43765e2594df8",
              "userId": "5ad4d64132c50e8831c828656af05055",
              "isOwn": false,
              "canEdit": true,
              "canDelete": true
            }
            """.trimIndent(),
        )
        allowed.mayEdit shouldBe true
        allowed.mayDelete shouldBe true

        val legacy = json.decodeFromString<CommentDto>(
            """
            {
              "id": "fc40a184b83c4517b280e4f5585e0404",
              "itemId": "62bd50f7c9916a91bfd43765e2594df8",
              "userId": "5ad4d64132c50e8831c828656af05055",
              "isOwn": true
            }
            """.trimIndent(),
        )
        legacy.mayEdit shouldBe true
        legacy.mayDelete shouldBe true
    }

    @Test
    fun `missing optional fields fall back to safe defaults`() {
        val comment = json.decodeFromString<CommentDto>(
            """
            {
              "id": "fc40a184b83c4517b280e4f5585e0404",
              "itemId": "62bd50f7c9916a91bfd43765e2594df8",
              "userId": "5ad4d64132c50e8831c828656af05055"
            }
            """.trimIndent(),
        )

        comment.username shouldBe ""
        comment.text shouldBe ""
        comment.replies shouldBe emptyList()
        comment.likeCount shouldBe 0
    }
}
