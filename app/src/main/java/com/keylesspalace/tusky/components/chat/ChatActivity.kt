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
import com.keylesspalace.tusky.adapter.TimelineAdapter
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Chat
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.interfaces.ChatActionListener
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.repository.ChatMesssageOrPlaceholder
import com.keylesspalace.tusky.repository.ChatRepository
import com.keylesspalace.tusky.viewdata.ChatMessageViewData
import kotlinx.android.synthetic.main.activity_chat.*
import kotlinx.android.synthetic.main.toolbar_basic.toolbar
import androidx.arch.core.util.Function
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.*
import com.keylesspalace.tusky.appstore.*
import com.keylesspalace.tusky.repository.Placeholder
import com.keylesspalace.tusky.repository.TimelineRequestMode
import com.keylesspalace.tusky.service.MessageToSend
import com.keylesspalace.tusky.service.ServiceClient
import com.keylesspalace.tusky.util.*
import com.uber.autodispose.android.lifecycle.autoDispose
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_chat.progressBar
import kotlinx.android.synthetic.main.fragment_timeline.*
import java.io.IOException
import java.lang.Exception
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ChatActivity: BottomSheetActivity(),
        Injectable, ChatActionListener {
    private val TAG = "ChatsActivity" // logging tag
    private val LOAD_AT_ONCE = 30

    @Inject
    lateinit var eventHub: EventHub
    @Inject
    lateinit var api: MastodonApi
    @Inject
    lateinit var chatsRepo: ChatRepository
    @Inject
    lateinit var serviceClient: ServiceClient

    lateinit var adapter: ChatMessagesAdapter

    private val msgs = PairedList<ChatMesssageOrPlaceholder, ChatMessageViewData?>(Function<ChatMesssageOrPlaceholder, ChatMessageViewData?> { input ->
        input.asRightOrNull()?.let(ViewDataUtils::chatMessageToViewData) ?:
            ChatMessageViewData.Placeholder(input.asLeft().id, false)
    })

    private var bottomLoading = false
    private var eventRegistered = false
    private var isNeedRefresh = false
    private var didLoadEverythingBottom = false
    private var initialUpdateFailed = false

    private enum class FetchEnd {
        TOP, BOTTOM, MIDDLE
    }

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
        val chatId = intent.getStringExtra(ID)
        val avatarUrl = intent.getStringExtra(AVATAR_URL)
        val displayName = intent.getStringExtra(DISPLAY_NAME)
        val username = intent.getStringExtra(USERNAME)
        val emojis = intent.getParcelableArrayListExtra<Emoji>(EMOJIS)

        if(chatId == null || avatarUrl == null || displayName == null || username == null || emojis == null) {
            throw IllegalArgumentException("Can't open ChatActivity without chat id")
        }
        this.chatId = chatId

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
        // recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        recycler.adapter = adapter

        sendButton.setOnClickListener {
            serviceClient.sendChatMessage( MessageToSend(
                    editText.text.toString(),
                    null,
                    null,
                    accountManager.activeAccount!!.id,
                    this.chatId,
                    0
            ))
        }

        if (!eventRegistered) {
            eventHub.events
                    .observeOn(AndroidSchedulers.mainThread())
                    .autoDispose(this, Lifecycle.Event.ON_DESTROY)
                    .subscribe { event: Event? ->
                        when(event) {
                            is ChatMessageDeliveredEvent -> {
                                onRefresh()
                                editText.text.clear()
                            }
                        }
                    }
            eventRegistered = true
        }

        tryCache()
    }

    private fun clearPlaceholdersForResponse(msgs: MutableList<ChatMesssageOrPlaceholder>) {
        msgs.removeAll { it.isLeft() }
    }

    private fun tryCache() {
        // Request timeline from disk to make it quick, then replace it with timeline from
        // the server to update it
        chatsRepo.getChatMessages(chatId, null, null, null, LOAD_AT_ONCE, TimelineRequestMode.DISK)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(this, Lifecycle.Event.ON_DESTROY)
                .subscribe { msgs ->
                    if (msgs.size > 1) {
                        val mutableMsgs = msgs.toMutableList()
                        clearPlaceholdersForResponse(mutableMsgs)
                        this.msgs.clear()
                        this.msgs.addAll(mutableMsgs)
                        updateAdapter()
                        progressBar.visibility = View.GONE
                        // Request statuses including current top to refresh all of them
                    }
                    updateCurrent()
                    loadAbove()
                }
    }

    private fun updateCurrent() {
        if (msgs.isEmpty()) {
            return
        }

        val topId  = msgs.first { it.isRight() }.asRight().id
        chatsRepo.getChatMessages(chatId, topId, null, null, LOAD_AT_ONCE, TimelineRequestMode.NETWORK)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(this, Lifecycle.Event.ON_DESTROY)
                .subscribe({ messages ->
                    initialUpdateFailed = false
                    // When cached timeline is too old, we would replace it with nothing
                    if (messages.isNotEmpty()) {
                        // clear old cached statuses
                        if(this.msgs.isNotEmpty()) {
                            this.msgs.removeAll {
                                if(it.isRight()) {
                                    val chat = it.asRight()
                                    chat.id.length < topId.length || chat.id < topId
                                } else {
                                    val placeholder = it.asLeft()
                                    placeholder.id.length < topId.length || placeholder.id < topId
                                }
                            }
                        }
                        this.msgs.addAll(messages)
                        updateAdapter()
                    }
                    bottomLoading = false
                }, {
                    initialUpdateFailed = true
                    // Indicate that we are not loading anymore
                    progressBar.visibility = View.GONE
                })
    }

    private fun showNothing() {
        messageView.visibility = View.VISIBLE
        messageView.setup(R.drawable.elephant_friend_empty, R.string.message_empty, null)
    }

    private fun loadAbove() {
        var firstOrNull: String? = null
        var secondOrNull: String? = null
        for (i in msgs.indices) {
            val msg = msgs[i]
            if (msg.isRight()) {
                firstOrNull = msg.asRight().id
                if (i + 1 < msgs.size && msgs[i + 1].isRight()) {
                    secondOrNull = msgs[i + 1].asRight().id
                }
                break
            }
        }
        if (firstOrNull != null) {
            sendFetchMessagesRequest(null, firstOrNull, secondOrNull, FetchEnd.TOP, -1)
        } else {
            sendFetchMessagesRequest(null, null, null, FetchEnd.BOTTOM, -1)
        }
    }

    private fun sendFetchMessagesRequest(maxId: String?, sinceId: String?,
                                      sinceIdMinusOne: String?,
                                      fetchEnd: FetchEnd, pos: Int) {
        // allow getting old statuses/fallbacks for network only for for bottom loading
        val mode = if (fetchEnd == FetchEnd.BOTTOM) {
            TimelineRequestMode.ANY
        } else {
            TimelineRequestMode.NETWORK
        }
        chatsRepo.getChatMessages(chatId, maxId, sinceId, sinceIdMinusOne, LOAD_AT_ONCE, mode)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(this, Lifecycle.Event.ON_DESTROY)
                .subscribe( { result -> onFetchTimelineSuccess(result.toMutableList(), fetchEnd, pos) },
                        { onFetchTimelineFailure(Exception(it), fetchEnd, pos) })
    }

    private fun updateAdapter() {
        Log.d(TAG, "updateAdapter")
        differ.submitList(msgs.pairedCopy)
    }

    private fun updateMessages(newMsgs: MutableList<ChatMesssageOrPlaceholder>, fullFetch: Boolean) {
        if (newMsgs.isEmpty()) {
            updateAdapter()
            return
        }
        if (msgs.isEmpty()) {
            msgs.addAll(newMsgs)
        } else {
            val lastOfNew = newMsgs[newMsgs.size - 1]
            val index = msgs.indexOf(lastOfNew)
            if (index >= 0) {
                msgs.subList(0, index).clear()
            }
            val newIndex = newMsgs.indexOf(msgs[0])
            if (newIndex == -1) {
                if (index == -1 && fullFetch) {
                    newMsgs.findLast { it.isRight() }?.let {
                        val placeholderId = it.asRight().id.inc()
                        newMsgs.add(Either.Left(Placeholder(placeholderId)))
                    }
                }
                msgs.addAll(0, newMsgs)
            } else {
                msgs.addAll(0, newMsgs.subList(0, newIndex))
            }
        }
        // Remove all consecutive placeholders
        removeConsecutivePlaceholders()
        updateAdapter()
    }

    private fun removeConsecutivePlaceholders() {
        for (i in 0 until msgs.size - 1) {
            if (msgs[i].isLeft() && msgs[i + 1].isLeft()) {
                msgs.removeAt(i)
            }
        }
    }

    private fun replacePlaceholderWithMessages(newMsgs: MutableList<ChatMesssageOrPlaceholder>,
                                            fullFetch: Boolean, pos: Int) {
        val placeholder = msgs[pos]
        if (placeholder.isLeft()) {
            msgs.removeAt(pos)
        }
        if (newMsgs.isEmpty()) {
            updateAdapter()
            return
        }
        if (fullFetch) {
            newMsgs.add(placeholder)
        }
        msgs.addAll(pos, newMsgs)
        removeConsecutivePlaceholders()
        updateAdapter()
    }

    private fun addItems(newMsgs: List<ChatMesssageOrPlaceholder>) {
        if (newMsgs.isEmpty()) {
            return
        }
        val last = msgs.findLast { it.isRight() }

        // I was about to replace findStatus with indexOf but it is incorrect to compare value
        // types by ID anyway and we should change equals() for Status, I think, so this makes sense
        if (last != null && !newMsgs.contains(last)) {
            msgs.addAll(newMsgs)
            removeConsecutivePlaceholders()
            updateAdapter()
        }
    }

    private fun onFetchTimelineSuccess(msgs: MutableList<ChatMesssageOrPlaceholder>,
                                       fetchEnd: FetchEnd, pos: Int) {

        // We filled the hole (or reached the end) if the server returned less statuses than we
        // we asked for.
        val fullFetch = msgs.size >= LOAD_AT_ONCE

        when (fetchEnd) {
            FetchEnd.TOP -> {
                updateMessages(msgs, fullFetch)
            }
            FetchEnd.MIDDLE -> {
                replacePlaceholderWithMessages(msgs, fullFetch, pos)
            }
            FetchEnd.BOTTOM -> {
                if (this.msgs.isNotEmpty() && !this.msgs.last().isRight()) {
                    this.msgs.removeAt(this.msgs.size - 1)
                    updateAdapter()
                }

                if (msgs.isNotEmpty() && !msgs.last().isRight()) {
                    // Removing placeholder if it's the last one from the cache
                    msgs.removeAt(msgs.size - 1)
                }

                val oldSize = this.msgs.size
                if (this.msgs.size > 1) {
                    addItems(msgs)
                } else {
                    updateMessages(msgs, fullFetch)
                }

                if (this.msgs.size == oldSize) {
                    // This may be a brittle check but seems like it works
                    // Can we check it using headers somehow? Do all server support them?
                    didLoadEverythingBottom = true
                }
            }
        }
        updateBottomLoadingState(fetchEnd)
        progressBar.visibility = View.GONE
        if (this.msgs.size == 0) {
            showNothing()
        } else {
            messageView.visibility = View.GONE
        }
    }

    private fun onRefresh() {
        messageView.visibility = View.GONE
        isNeedRefresh = false

        if (this.initialUpdateFailed) {
            updateCurrent()
        }
        loadAbove()
    }

    private fun onFetchTimelineFailure(exception: Exception, fetchEnd: FetchEnd, position: Int) {
        topProgressBar.hide()
        if (fetchEnd == FetchEnd.MIDDLE && !msgs[position].isRight()) {
            var placeholder = msgs[position].asLeftOrNull()
            val newViewData: ChatMessageViewData
            if (placeholder == null) {
                val msg = msgs[position - 1].asRight()
                val newId = msg.id.dec()
                placeholder = Placeholder(newId)
            }
            newViewData = ChatMessageViewData.Placeholder(placeholder.id, false)
            msgs.setPairedItem(position, newViewData)
            updateAdapter()
        } else if (msgs.isEmpty()) {
            messageView.visibility = View.VISIBLE
            if (exception is IOException) {
                messageView.setup(R.drawable.elephant_offline, R.string.error_network) {
                    progressBar.visibility = View.VISIBLE
                    onRefresh()
                }
            } else {
                messageView.setup(R.drawable.elephant_error, R.string.error_generic) {
                    progressBar.visibility = View.VISIBLE
                    onRefresh()
                }
            }
        }
        Log.e(TAG, "Fetch Failure: " + exception.message)
        updateBottomLoadingState(fetchEnd)
        progressBar.visibility = View.GONE
    }

    private fun updateBottomLoadingState(fetchEnd: FetchEnd) {
        if (fetchEnd == FetchEnd.BOTTOM) {
            bottomLoading = false
        }
    }

    override fun onLoadMore(position: Int) {
        //check bounds before accessing list,
        if (msgs.size >= position && position > 0) {
            val fromChat = msgs[position - 1].asRightOrNull()
            val toChat = msgs[position + 1].asRightOrNull()
            if (fromChat == null || toChat == null) {
                Log.e(TAG, "Failed to load more at $position, wrong placeholder position")
                return
            }

            val maxMinusOne = if (msgs.size > position + 1 && msgs[position + 2].isRight()) msgs[position + 1].asRight().id else null
            sendFetchMessagesRequest(fromChat.id, toChat.id, maxMinusOne,
                    FetchEnd.MIDDLE, position)

            val (id) = msgs[position].asLeft()
            val newViewData = ChatMessageViewData.Placeholder(id, true)
            msgs.setPairedItem(position, newViewData)
            updateAdapter()
        } else {
            Log.e(TAG, "error loading more")
        }
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

    override fun onResume() {
        super.onResume()
        startUpdateTimestamp()
    }

    /**
     * Start to update adapter every minute to refresh timestamp
     * If setting absoluteTimeView is false
     * Auto dispose observable on pause
     */
    private fun startUpdateTimestamp() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false)
        if (!useAbsoluteTime) {
            Observable.interval(1, TimeUnit.MINUTES)
                    .observeOn(AndroidSchedulers.mainThread())
                    .autoDispose(this, Lifecycle.Event.ON_PAUSE)
                    .subscribe { updateAdapter() }
        }
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
