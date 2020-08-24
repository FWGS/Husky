package com.keylesspalace.tusky.components.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewTagActivity
import com.keylesspalace.tusky.adapter.ChatMessagesAdapter
import com.keylesspalace.tusky.adapter.ChatMessagesViewHolder
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.adapter.TimelineAdapter
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Chat
import com.keylesspalace.tusky.entity.ChatMessage
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.interfaces.ChatActionListener
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.repository.ChatMessageStatus
import com.keylesspalace.tusky.repository.ChatRepository
import com.keylesspalace.tusky.repository.ChatStatus
import com.keylesspalace.tusky.viewdata.ChatMessageViewData
import com.keylesspalace.tusky.viewdata.ChatViewData
import kotlinx.android.synthetic.main.activity_chat.*
import kotlinx.android.synthetic.main.toolbar_basic.toolbar
import androidx.arch.core.util.Function
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.*
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.repository.TimelineRequestMode
import com.keylesspalace.tusky.util.*
import com.uber.autodispose.android.lifecycle.autoDispose
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_chat.progressBar
import kotlinx.android.synthetic.main.fragment_timeline.*
import java.lang.Exception
import javax.inject.Inject

class ChatActivity: BottomSheetActivity(),
        Injectable, ChatActionListener {
    private val TAG = "ChatsF" // logging tag
    private val LOAD_AT_ONCE = 30

    @Inject
    lateinit var eventHub: EventHub
    @Inject
    lateinit var api: MastodonApi
    @Inject
    lateinit var chatsRepo: ChatRepository

    lateinit var adapter: ChatMessagesAdapter

    private val msgs = PairedList<ChatMessageStatus, ChatMessageViewData?>(Function<ChatMessageStatus, ChatMessageViewData?> {input ->
        input.asRightOrNull()?.let(ViewDataUtils::chatMessageToViewData) ?:
            ChatMessageViewData.Placeholder(input.asLeft().id, false)
    })

    private val listUpdateCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            Log.d(TAG, "onInserted")
            adapter.notifyItemRangeInserted(position, count)
            if (position == 0) {
                recycler.scrollToPosition(0)
            }
        }

        override fun onRemoved(position: Int, count: Int) {
            Log.d(TAG, "onRemoved")
            adapter.notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            Log.d(TAG, "onMoved")
            adapter.notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            Log.d(TAG, "onChanged")
            adapter.notifyItemRangeChanged(position, count, payload)
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<ChatMessageViewData>() {
        override fun areItemsTheSame(oldItem: ChatMessageViewData, newItem: ChatMessageViewData): Boolean {
            return oldItem.getViewDataId() == newItem.getViewDataId()
        }

        override fun areContentsTheSame(oldItem: ChatMessageViewData, newItem: ChatMessageViewData): Boolean {
            return false // Items are different always. It allows to refresh timestamp on every view holder update
        }

        override fun getChangePayload(oldItem: ChatMessageViewData, newItem: ChatMessageViewData): Any? {
            return if (oldItem.deepEquals(newItem)) {
                //If items are equal - update timestamp only
                listOf(ChatMessagesViewHolder.Key.KEY_CREATED)
            } else  // If items are different - update a whole view holder
                null
        }
    }

    private val differ = AsyncListDiffer(listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build())

    private val dataSource = object : TimelineAdapter.AdapterDataSource<ChatMessageViewData> {
        override fun getItemCount(): Int {
            return differ.currentList.size
        }

        override fun getItemAt(pos: Int): ChatMessageViewData {
            return differ.currentList[pos]
        }
    }

    private lateinit var chatId : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatId = intent.getStringExtra(ID)
        val avatarUrl = intent.getStringExtra(AVATAR_URL)
        val displayName = intent.getStringExtra(DISPLAY_NAME)
        val username = intent.getStringExtra(USERNAME)
        val emojis = intent.getParcelableArrayListExtra<Emoji>(EMOJIS)

        if(chatId == null || avatarUrl == null || displayName == null || username == null || emojis == null) {
            throw IllegalArgumentException("Can't open ChatActivity without chat id")
        }

        if(accountManager.activeAccount == null) {
            throw Exception("No active account!")
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

        val statusDisplayOptions = StatusDisplayOptions(
                false,
                false,
                true,
                false,
                false,
                CardViewMode.NONE,
                false)

        adapter = ChatMessagesAdapter(dataSource, this, statusDisplayOptions, accountManager.activeAccount!!.accountId)

        // TODO: a11y
        recycler.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.reverseLayout = true
        recycler.layoutManager = layoutManager
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        recycler.adapter = adapter

        tryCache()
    }

    private fun tryCache() {
        // Request timeline from disk to make it quick, then replace it with timeline from
        // the server to update it
        chatsRepo.getChatMessages(chatId, null, null, null, LOAD_AT_ONCE, TimelineRequestMode.DISK)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(this, Lifecycle.Event.ON_DESTROY)
                .subscribe { msgs ->
                    if (msgs.size > 1) {
                        val mutableChats = msgs.toMutableList()
                        this.msgs.clear()
                        this.msgs.addAll(mutableChats)
                        updateAdapter()
                        progressBar.visibility = View.GONE
                        // Request statuses including current top to refresh all of them
                    }
                }
    }

    private fun updateAdapter() {
        Log.d(TAG, "updateAdapter")
        differ.submitList(msgs.pairedCopy)
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

    override fun onViewAccount(id: String) {
        viewAccount(id)
    }

    override fun onViewUrl(url: String) {
        viewUrl(url)
    }

    override fun onViewTag(tag: String) {
        val intent = Intent(this, ViewTagActivity::class.java)
        intent.putExtra("hashtag", tag)
        startActivity(intent)
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
