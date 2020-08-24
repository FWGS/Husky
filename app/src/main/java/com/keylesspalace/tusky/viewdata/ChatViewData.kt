package com.keylesspalace.tusky.viewdata

import android.text.Spanned
import com.keylesspalace.tusky.entity.*
import java.util.*


abstract class ChatViewData {
    abstract fun getViewDataId() : Long
    abstract fun deepEquals(o: ChatViewData) : Boolean

    class Concrete(val account : Account,
        val id: String,
        val unread: Long,
        val lastMessage: ChatMessageViewData.Concrete?,
        val updatedAt: Date ) : ChatViewData() {
        override fun getViewDataId(): Long {
            return id.hashCode().toLong()
        }

        override fun deepEquals(o: ChatViewData): Boolean {
            if (o !is Concrete) return false
            return Objects.equals(o.account, account)
                    && Objects.equals(o.id, id)
                    && o.unread == unread
                    && (lastMessage == o.lastMessage || (lastMessage != null && o.lastMessage != null && o.lastMessage.deepEquals(lastMessage)))
                    && Objects.equals(o.updatedAt, updatedAt)
        }

        override fun hashCode(): Int {
            return Objects.hash(account, id, unread, lastMessage, updatedAt)
        }
    }

    class Placeholder(val id: String, val isLoading: Boolean) : ChatViewData() {
        override fun getViewDataId(): Long {
            return id.hashCode().toLong()
        }

        override fun deepEquals(o: ChatViewData): Boolean {
            if( o !is Placeholder ) return false
            return o.isLoading == isLoading && o.id == id
        }
    }
}

abstract class ChatMessageViewData {
    abstract fun getViewDataId() : Long
    abstract fun deepEquals(o: ChatMessageViewData) : Boolean

    class Concrete(val id: String,
                   val content: Spanned,
                   val chatId: String,
                   val accountId: String,
                   val createdAt: Date,
                   val attachment: Attachment?,
                   val emojis: List<Emoji>) : ChatMessageViewData()
    {
        override fun getViewDataId(): Long {
            return id.hashCode().toLong()
        }

        override fun deepEquals(o: ChatMessageViewData): Boolean {
            if( o !is Concrete ) return false

            return Objects.equals(o.id, id)
                    && Objects.equals(o.content, content)
                    && Objects.equals(o.chatId, chatId)
                    && Objects.equals(o.accountId, accountId)
                    && Objects.equals(o.createdAt, createdAt)
                    && Objects.equals(o.attachment, attachment)
                    && Objects.equals(o.emojis, emojis)
        }

        override fun hashCode() : Int {
            return Objects.hash(id, content, chatId, accountId, createdAt, attachment)
        }
    }

    class Placeholder(val id: String, val isLoading: Boolean) : ChatMessageViewData() {
        override fun getViewDataId(): Long {
            return id.hashCode().toLong()
        }

        override fun deepEquals(o: ChatMessageViewData): Boolean {
            if( o !is Placeholder) return false
            return o.isLoading == isLoading && o.id == id
        }
    }
}