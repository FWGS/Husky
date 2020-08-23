package com.keylesspalace.tusky.components.chat

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Chat
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.repository.ChatRepository
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar
import kotlinx.android.synthetic.main.activity_chat.*
import kotlinx.android.synthetic.main.toolbar_basic.*
import kotlinx.android.synthetic.main.toolbar_basic.toolbar
import javax.inject.Inject

class ChatActivity: BaseActivity(),
        Injectable {

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var api: MastodonApi

    @Inject
    lateinit var chatsRepo: ChatRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val chatId = intent.getStringExtra(ID)
        val avatarUrl = intent.getStringExtra(AVATAR_URL)
        val displayName = intent.getStringExtra(DISPLAY_NAME)
        val username = intent.getStringExtra(USERNAME)
        val emojis = intent.getParcelableArrayListExtra<Emoji>(EMOJIS)

        if(chatId == null || avatarUrl == null || displayName == null || username == null || emojis == null) {
            throw IllegalArgumentException("Can't open ChatActivity without chat id")
        }

        setContentView(R.layout.activity_chat)
        setSupportActionBar(toolbar)

        supportActionBar?.run {
            title = ""
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        loadAvatar(avatarUrl, chatAvatar,
                resources.getDimensionPixelSize(R.dimen.avatar_radius_24dp),true)

        chatTitle.text = displayName.emojify(emojis, chatTitle, true)
        chatUsername.text = username
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        fun getIntent(context: Context, chat: Chat) : Intent {
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra(ID, chat.id)
            intent.putExtra(AVATAR_URL, chat.account.avatar)
            intent.putExtra(DISPLAY_NAME, chat.account.displayName ?: chat.account.localUsername)
            intent.putParcelableArrayListExtra(EMOJIS, ArrayList(chat.account.emojis ?: emptyList<Emoji>()))
            intent.putExtra(USERNAME, chat.account.username)
            return intent
        }

        const val ID = "id"
        const val AVATAR_URL = "avatar_url"
        const val DISPLAY_NAME = "display_name"
        const val USERNAME = "username"
        const val EMOJIS = "emojis"
    }

}
