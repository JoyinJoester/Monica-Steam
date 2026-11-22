/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.monica.telegramchat.upstream.EmojiData;
import com.monica.telegramchat.upstream.EmojiPack;
import com.monica.telegramchat.upstream.ImageViewEmoji;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import static com.monica.telegramchat.TelegramUi.dp;

/** Host for official EmojiData/EmojiPack/ImageViewEmoji, without Telegram account services. */
public final class TelegramEmojiPickerView extends LinearLayout {
    /** Space reserved for the floating page pill drawn by the Compose host. */
    private static final int BOTTOM_INSET_DP = 68;
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> BITMAPS = new LruCache<>(128);
    private static final Map<String, int[]> INDEX = new HashMap<>();
    static {
        for (int p = 0; p < EmojiData.data.length; p++) {
            for (int i = 0; i < EmojiData.data[p].length; i++) {
                String code = EmojiData.data[p][i];
                INDEX.put(code, new int[]{p, i});
                INDEX.putIfAbsent(code.replace("\ufe0f", ""), new int[]{p, i});
            }
        }
    }
    private final RecyclerView grid;
    private final GridLayoutManager layout;
    private final LinearLayout categoryTabs;
    private final List<String> recent = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private List<String> values = Collections.emptyList();
    private Consumer<String> onSelected = code -> {};
    private Runnable onClose = () -> {};
    private TelegramChatColors colors = TelegramChatColors.DAY;
    private int category = 0;
    private boolean transparentBackground;

    public TelegramEmojiPickerView(Context context) {
        super(context); TelegramUi.initialize(context); setOrientation(VERTICAL);
        String saved = context.getSharedPreferences("steam_unicode_recent", Context.MODE_PRIVATE).getString("values", "");
        if (!saved.isEmpty()) recent.addAll(Arrays.asList(saved.split("\n")));
        if (recent.isEmpty()) recent.addAll(Arrays.asList("😀", "😂", "🥰", "👍", "❤️", "🎮", "🎉", "😊"));
        HorizontalScrollView strip = new HorizontalScrollView(context);
        strip.setHorizontalScrollBarEnabled(false);
        categoryTabs = new LinearLayout(context);
        strip.addView(categoryTabs, new ViewGroup.LayoutParams(-2, -1));
        addView(strip, new LayoutParams(-1, dp(40)));
        String[] names = {"最近", "笑脸与人物", "动物与自然", "食物与饮料", "活动", "旅行与地点", "物品", "符号", "旗帜"};
        ImageViewEmoji clock = new ImageViewEmoji(context);
        clock.setImageDrawable(new RecentIconDrawable());
        clock.setContentDescription(names[0]);
        clock.setOnClickListener(v -> selectCategory(0));
        categoryTabs.addView(clock, new LayoutParams(dp(40), dp(40)));
        for (int p = 0; p < EmojiData.dataColored.length; p++) {
            final int page = p;
            ImageViewEmoji tab = new ImageViewEmoji(context);
            tab.setPadding(dp(6), dp(6), dp(6), dp(6));
            tab.setContentDescription(names[Math.min(p + 1, names.length - 1)]);
            bindBitmap(tab, EmojiData.dataColored[p][0], 24);
            tab.setOnClickListener(v -> selectCategory(page + 1));
            categoryTabs.addView(tab, new LayoutParams(dp(40), dp(40)));
        }
        grid = new RecyclerView(context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                // EmojiView.EmojiGridView source phone span calculation (45dp cells).
                int spans = Math.max(1, MeasureSpec.getSize(widthSpec) / dp(45));
                if (layout.getSpanCount() != spans) layout.setSpanCount(spans);
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        layout = new GridLayoutManager(context, 8); grid.setLayoutManager(layout);
        // The page switcher is a floating pill owned by the Compose host, so the
        // grid keeps a matching bottom inset instead of carrying its own footer.
        grid.setPadding(dp(5), 0, dp(5), dp(BOTTOM_INSET_DP)); grid.setClipToPadding(false);
        grid.setAdapter(adapter);
        grid.setItemAnimator(null);
        addView(grid, new LayoutParams(-1, 0, 1));
        selectCategory(0); setColors(colors);
    }
    public void setCallbacks(Consumer<String> select, Runnable close) {
        onSelected = select; onClose = close;
    }
    public void setColors(TelegramChatColors colors) {
        this.colors = colors;
        setBackgroundColor(transparentBackground ? android.graphics.Color.TRANSPARENT : colors.menuBackground);
        ((ImageViewEmoji)categoryTabs.getChildAt(0)).setColorFilter(colors.secondaryText);
        applyCategorySelection();
    }
    /** The Compose host supplies the blurred backdrop; keep emoji and category icons sharp. */
    public void setTransparentBackground(boolean enabled) {
        if (transparentBackground == enabled) return;
        transparentBackground = enabled;
        setBackgroundColor(enabled ? android.graphics.Color.TRANSPARENT : colors.menuBackground);
    }
    private void selectCategory(int page) {
        category = page;
        values = page == 0 ? new ArrayList<>(recent) : Arrays.asList(EmojiData.dataColored[page - 1]);
        adapter.notifyDataSetChanged();
        if (grid != null) grid.scrollToPosition(0);
        applyCategorySelection();
    }
    /** Telegram rings the active category tab instead of only dimming the others. */
    private void applyCategorySelection() {
        for (int i = 0; i < categoryTabs.getChildCount(); i++) {
            View tab = categoryTabs.getChildAt(i);
            boolean selected = i == category;
            tab.setAlpha(selected ? 1f : .5f);
            tab.setSelected(selected);
            if (!selected) { tab.setBackground(null); continue; }
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(0);
            ring.setStroke(Math.max(1, dp(1.5f)), colors.secondaryText);
            tab.setBackground(new InsetDrawable(ring, Math.max(1, Math.round(dp(4f)))));
        }
    }
    private void choose(String code) {
        recent.remove(code); recent.add(0, code);
        while (recent.size() > 48) recent.remove(recent.size() - 1);
        getContext().getSharedPreferences("steam_unicode_recent", Context.MODE_PRIVATE).edit().putString("values", String.join("\n", recent)).apply();
        onSelected.accept(code);
    }
    private static void bindBitmap(ImageViewEmoji view, String code, int size) {
        view.setTag(code); view.setImageDrawable(null);
        Bitmap cached; synchronized (BITMAPS) { cached = BITMAPS.get(code); }
        if (cached != null) { show(view, cached, size); return; }
        WeakReference<ImageViewEmoji> target = new WeakReference<>(view);
        DECODER.execute(() -> {
            int[] index = INDEX.get(code);
            if (index == null) index = INDEX.get(code.replace("\ufe0f", ""));
            if (index == null && EmojiData.emojiAliasMap.containsKey(code)) index = INDEX.get(EmojiData.emojiAliasMap.get(code));
            if (index == null) return;
            Bitmap bitmap = EmojiPack.getInstance().getEmoji(index[0], index[1]);
            if (bitmap == null) return;
            synchronized (BITMAPS) { BITMAPS.put(code, bitmap); }
            MAIN.post(() -> { ImageViewEmoji current = target.get(); if (current != null && code.equals(current.getTag())) show(current, bitmap, size); });
        });
    }
    private static void show(ImageViewEmoji view, Bitmap bitmap, int size) {
        BitmapDrawable drawable = new BitmapDrawable(view.getResources(), bitmap) {
            @Override public int getIntrinsicWidth() { return dp(size); }
            @Override public int getIntrinsicHeight() { return dp(size); }
        };
        drawable.setBounds(0, 0, dp(size), dp(size));
        // Pack bitmaps can inherit device density from BitmapFactory. Explicit
        // intrinsic dimensions avoid applying device density a second time.
        view.setImageDrawable(drawable);
    }
    private final class Adapter extends RecyclerView.Adapter<Holder> {
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            return new Holder(new ImageViewEmoji(parent.getContext()));
        }
        public void onBindViewHolder(Holder holder, int position) {
            String code = values.get(position);
            bindBitmap(holder.view, code, 32); holder.view.setContentDescription(code);
            holder.view.setOnClickListener(v -> choose(code));
        }
        public int getItemCount() { return values.size(); }
    }
    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageViewEmoji view;
        Holder(ImageViewEmoji view) { super(view); this.view = view; view.setLayoutParams(new RecyclerView.LayoutParams(-1, -2)); }
    }

    /** Square vector geometry keeps the clock centered independently of font metrics and font scale. */
    private static final class RecentIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RecentIconDrawable() {
            paint.setColor(0xffffffff);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.7f);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override public void draw(Canvas canvas) {
            float scale = Math.min(getBounds().width(), getBounds().height()) / 24f;
            canvas.save();
            canvas.translate(getBounds().exactCenterX(), getBounds().exactCenterY());
            canvas.scale(scale, scale);
            canvas.drawCircle(0, 0, 9f, paint);
            canvas.drawLine(0, -5f, 0, 0, paint);
            canvas.drawLine(0, 0, 4f, 2.5f, paint);
            canvas.restore();
        }
        @Override public int getIntrinsicWidth() { return dp(24); }
        @Override public int getIntrinsicHeight() { return dp(24); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
