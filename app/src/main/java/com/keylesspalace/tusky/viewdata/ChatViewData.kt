package com.keylesspalace.tusky.viewdata

import com.keylesspalace.tusky.entity.*
import java.util.*


abstract class ChatViewData {
    abstract fun getViewDataId() : Int
    abstract fun deepEquals(val o: ChatViewData) : Boolean

    class Concrete(val account : Account,
        val id: String,
        val unread: Int,
        val lastMessage: ChatMessageViewData.Concrete?,
        val updatedAt: Date ) : ChatViewData() {
        override fun getViewDataId(): Int {
            return id.hashCode()
        }

        override fun deepEquals(o: ChatViewData): Boolean {
            if (o !is Concrete) return false
            return o.account == account && o.id == id &&
                    o.unread == unread &&
                    (lastMessage != null && o.lastMessage?.deepEquals(lastMessage) ?: false) &&
                    o.updatedAt == updatedAt
        }

        override fun hashCode(): Int {
            return Objects.hash(account, id, unread, lastMessage, updatedAt)
        }
    }

    class Placeholder(val id: Int, val isLoading: Boolean) : ChatViewData() {
        override fun getViewDataId(): Int {
            return id
        }

        override fun deepEquals(val o: ChatViewData): Boolean {
            if( o !is Placeholder ) return false
            return o.isLoading == isLoading && o.id == id
        }
    }
}

abstract class ChatMessageViewData {
    abstract fun getViewDataId() : Int
    abstract fun deepEquals(val o: ChatMessageViewData) : Boolean

    class Concrete(val id: String,
        val content: String,
        val chatId: String,
        val accountId: String,
        val createdAt: Date,
        val attachment: Attachment?,
        val emojis: List<Emoji>) : ChatMessageViewData()
    {
        override fun getViewDataId(): Int {
            return id.hashCode()
        }

        override fun deepEquals(o: ChatMessageViewData): Boolean {
            if( o !is Concrete ) return false

            return o.id == id && o.content == content && o.chatId == chatId &&
                    o.accountId == accountId && o.createdAt == createdAt &&
                    o.attachment == attachment && o.emojis == emojis
        }

        override fun hashCode() : Int {
            return Objects.hash(id, content, chatId, accountId, createdAt, attachment)
        }
    }

    class Placeholder(val id: Int, private val isLoading: Boolean) : ChatMessageViewData() {
        override fun getViewDataId(): Int {
            return id
        }

        override fun deepEquals(val o: ChatMessageViewData): Boolean {
            if( o !is Placeholder) return false
            return o.isLoading == isLoading && o.id == id
        }
    }
}