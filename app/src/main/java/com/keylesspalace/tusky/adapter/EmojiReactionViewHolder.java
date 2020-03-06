package com.keylesspalace.tusky.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.emoji.widget.EmojiAppCompatButton;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.flexbox.FlexboxLayoutManager;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.entity.EmojiReaction;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CardViewMode;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.StatusDisplayOptions;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.text.DateFormat;
import java.util.List;
import java.util.Date;

public class EmojiReactionViewHolder extends RecyclerView.ViewHolder {
    public EmojiAppCompatButton emojiReaction;
    EmojiReactionViewHolder(View view) {
        super(view);
        emojiReaction = view.findViewById(R.id.status_emoji_reaction);
    }
}
 
