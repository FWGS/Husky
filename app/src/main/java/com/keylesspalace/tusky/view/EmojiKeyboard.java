package com.keylesspalace.tusky.view;

import android.view.*;
import android.content.*;
import android.util.*;
import android.widget.*;
import android.app.*;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.adapter.UnicodeEmojiAdapter;

public class EmojiKeyboard extends LinearLayout {
    private TabLayout tabs;
    private ViewPager2 pager;
    private TabLayoutMediator currentMediator;

    public EmojiKeyboard(Context context) {
        super(context);
        init(context);
    }

    public EmojiKeyboard(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public EmojiKeyboard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
        
    void init(Context context) {
        inflate(context, R.layout.item_emoji_picker, this);
        
        tabs = findViewById(R.id.picker_tabs);
        pager = findViewById(R.id.picker_pager);
    }
    
    public static final int UNICODE_MODE = 0;
    public static final int CUSTOM_MODE  = 1;
    public static final int STICKER_MODE = 2;
    
    void setupKeyboard(String id, int mode, OnEmojiSelectedListener listener) {
        RecyclerView.Adapter adapter;
    
        switch(mode) {
	        case CUSTOM_MODE:
	        // UNDONE
            //break;
            case STICKER_MODE:
            // UNDONE
            //break;
            default:
            case UNICODE_MODE:
                adapter = new UnicodeEmojiAdapter(id, listener);
            break;
        }
        
        pager.setAdapter(adapter);
        
        if(currentMediator != null)
	        currentMediator.detach();
        
        currentMediator = new TabLayoutMediator(tabs, pager, (TabLayoutMediator.TabConfigurationStrategy)adapter);
        currentMediator.attach();
    }
    
    public interface OnEmojiSelectedListener {
        void onEmojiSelected(String id, String emoji);
    }
    
    public static void show(Context ctx, String id, int mode, OnEmojiSelectedListener listener) {
        final Dialog dialog = new Dialog(ctx);
        
        dialog.setTitle(null);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_emoji_keyboard);
        EmojiKeyboard kbd = (EmojiKeyboard)dialog.findViewById(R.id.dialog_emoji_keyboard);
        kbd.setupKeyboard(id, mode, (_id, _emoji) -> {
            listener.onEmojiSelected(_id, _emoji);
            dialog.dismiss();
        });
        
        dialog.show();
    }
}
