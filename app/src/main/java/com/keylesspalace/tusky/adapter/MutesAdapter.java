package com.keylesspalace.tusky.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.interfaces.AccountActionListener;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.pkmmte.view.CircularImageView;
import com.squareup.picasso.Picasso;

public class MutesAdapter extends AccountAdapter {
    private static final int VIEW_TYPE_MUTED_USER = 0;
    private static final int VIEW_TYPE_FOOTER = 1;

    public MutesAdapter(AccountActionListener accountActionListener) {
        super(accountActionListener);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_MUTED_USER: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_muted_user, parent, false);
                return new MutesAdapter.MutedUserViewHolder(view);
            }
            case VIEW_TYPE_FOOTER: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_footer, parent, false);
                return new FooterViewHolder(view);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        if (position < accountList.size()) {
            MutedUserViewHolder holder = (MutedUserViewHolder) viewHolder;
            holder.setupWithAccount(accountList.get(position));
            holder.setupActionListener(accountActionListener, position);
        } else {
            FooterViewHolder holder = (FooterViewHolder) viewHolder;
            holder.setState(footerState);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == accountList.size()) {
            return VIEW_TYPE_FOOTER;
        } else {
            return VIEW_TYPE_MUTED_USER;
        }
    }

    static class MutedUserViewHolder extends RecyclerView.ViewHolder {
        private CircularImageView avatar;
        private TextView username;
        private TextView displayName;
        private ImageButton unmute;
        private String id;

        MutedUserViewHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.muted_user_avatar);
            username = itemView.findViewById(R.id.muted_user_username);
            displayName = itemView.findViewById(R.id.muted_user_display_name);
            unmute = itemView.findViewById(R.id.muted_user_unmute);
        }

        void setupWithAccount(Account account) {
            id = account.getId();
            CharSequence emojifiedName = CustomEmojiHelper.emojifyString(account.getName(), account.getEmojis(), displayName);
            displayName.setText(emojifiedName);
            String format = username.getContext().getString(R.string.status_username_format);
            String formattedUsername = String.format(format, account.getUsername());
            username.setText(formattedUsername);
            Picasso.with(avatar.getContext())
                    .load(account.getAvatar())
                    .placeholder(R.drawable.avatar_default)
                    .into(avatar);
        }

        void setupActionListener(final AccountActionListener listener, final int position) {
            unmute.setOnClickListener(v -> listener.onMute(false, id, position));
            avatar.setOnClickListener(v -> listener.onViewAccount(id));
        }
    }
}
