package com.keylesspalace.tusky.adapter;

import android.view.*;
import android.util.*;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.flexbox.FlexboxLayoutManager;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.*;
import androidx.emoji.widget.EmojiAppCompatButton;
import androidx.emoji.text.EmojiCompat;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.view.EmojiKeyboard;
import com.keylesspalace.tusky.util.Emojis;

public class UnicodeEmojiAdapter
    extends RecyclerView.Adapter<SingleViewHolder>
    implements TabLayoutMediator.TabConfigurationStrategy  {
    
    private String id;
    private EmojiKeyboard.OnEmojiSelectedListener listener;
    
    private final static float BUTTON_WIDTH_DP = 65.0f; // empirically found value :(
    
    public UnicodeEmojiAdapter(String id, EmojiKeyboard.OnEmojiSelectedListener listener) {
        super();
        this.id = id;
        this.listener = listener;
    }
    
    @Override
    public void onConfigureTab(TabLayout.Tab tab, int position) {
        tab.setText(Emojis.EMOJIS[position][0]);
    }
        
    @Override
    public int getItemCount() {
        return Emojis.EMOJIS.length;
    }
    
    @Override
    public void onBindViewHolder(SingleViewHolder holder, int position) {
        ((RecyclerView)holder.itemView).setAdapter(new UnicodeEmojiPageAdapter(Emojis.EMOJIS[position], id, listener));
    }
    
    @Override
    public SingleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_emoji_keyboard_page, parent, false);
        SingleViewHolder holder = new SingleViewHolder(view);
        
        DisplayMetrics dm = parent.getContext().getResources().getDisplayMetrics();
        float wdp = dm.widthPixels / dm.density;
        int rows = (int) (wdp / BUTTON_WIDTH_DP + 0.5);
        
        ((RecyclerView)view).setLayoutManager(new GridLayoutManager(view.getContext(), rows));
        return holder;
    }

    private class UnicodeEmojiPageAdapter extends RecyclerView.Adapter<SingleViewHolder> {
        private final String[] emojis;
        private final String id;
        private final EmojiKeyboard.OnEmojiSelectedListener listener;
        
        public UnicodeEmojiPageAdapter(String[] emojis, String id, EmojiKeyboard.OnEmojiSelectedListener listener) {
            this.emojis = emojis;
            this.id = id;
            this.listener = listener;
        }
        
        @Override
        public int getItemCount() {
            return emojis.length;
        }
        
        @Override
        public void onBindViewHolder(SingleViewHolder holder, int position) {
            String emoji = emojis[position];
            EmojiAppCompatButton btn = (EmojiAppCompatButton)holder.itemView;
            
            btn.setText(emoji);
            btn.setOnClickListener(v -> listener.onEmojiSelected(id, emoji));
        }
        
        @Override
        public SingleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_emoji_keyboard_emoji, parent, false);
            return new SingleViewHolder(view);
        }
    }
    
}
 
