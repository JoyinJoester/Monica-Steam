/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.monica.telegramchat.upstream.PhotoAttachPhotoCell;
import com.monica.telegramchat.upstream.SharedDocumentPickerCell;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import static com.monica.telegramchat.TelegramUi.dp;

/**
 * Original Telegram photo/document cells and attachment tabs, with host-owned URIs.
 *
 * <p>Telegram gives the picker almost the whole sheet and keeps a single slim header
 * (album + confirm) above the grid. The previous build stacked a spinner row, a search
 * field, a three-button action row and a status line before the content, so the actual
 * grid only got ~200dp. Everything optional now lives in the header overflow or in the
 * empty-state row, and the header is one 52dp line.
 */
public final class TelegramAttachmentPickerView extends LinearLayout {
    public static final class Entry {
        public final String uri, name, mime, album;
        public final long size, modified, duration;
        public final boolean directory;
        public Entry(String uri, String name, String mime, String album, long size, long modified, long duration, boolean directory) {
            this.uri = uri; this.name = name; this.mime = mime; this.album = album;
            this.size = size; this.modified = modified; this.duration = duration; this.directory = directory;
        }
    }
    private static final ExecutorService THUMBNAILS = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int COLUMNS = 3;

    private final RecyclerView list;
    private final Adapter adapter = new Adapter();
    private final LinearLayout notice;
    private final TextView album, send, overflow, status, grant, system, folder;
    private final EditText search;
    private final TelegramAttachmentTabsView tabs;
    private List<Entry> entries = Collections.emptyList(), visible = Collections.emptyList();
    private List<String> albumNames = Collections.singletonList("最近照片");
    private Entry selected;
    private String albumFilter = "", query = "";
    private boolean files, sortingByName, mediaAccess = true, empty;
    private TelegramChatColors colors = TelegramChatColors.DAY;
    private Consumer<Entry> onSend = entry -> {}, onFolder = entry -> {};
    private Runnable onGrant = () -> {}, onProvider = () -> {}, onChooseFolder = () -> {};
    private Runnable onGalleryProvider = () -> {};

    public TelegramAttachmentPickerView(Context context) {
        super(context); TelegramUi.initialize(context); setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), 0, dp(4), 0);
        album = label("最近照片  ▾", 16);
        album.setGravity(Gravity.CENTER_VERTICAL);
        album.setSingleLine(true);
        album.setEllipsize(TextUtils.TruncateAt.END);
        album.setOnClickListener(v -> showAlbums());
        header.addView(album, new LayoutParams(0, -1, 1));

        search = new EditText(context);
        search.setSingleLine();
        search.setTextSize(16);
        search.setHint("搜索文件");
        search.setBackgroundColor(0);
        search.setPadding(0, 0, dp(8), 0);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { query = s.toString(); filter(); }
            public void afterTextChanged(Editable e) {}
        });
        header.addView(search, new LayoutParams(0, -1, 1));

        send = label("选择", 14);
        send.setGravity(Gravity.CENTER);
        send.setPadding(dp(12), 0, dp(12), 0);
        send.setOnClickListener(v -> { if (selected != null) onSend.accept(selected); });
        send.setContentDescription("确认选择附件");
        header.addView(send, new LayoutParams(-2, -1));

        folder = label("文件夹", 13);
        folder.setGravity(Gravity.CENTER);
        folder.setPadding(dp(10), 0, dp(10), 0);
        folder.setContentDescription("选择文件夹");
        folder.setOnClickListener(v -> onChooseFolder.run());
        header.addView(folder, new LayoutParams(-2, -1));

        overflow = label("⋮", 20);
        overflow.setGravity(Gravity.CENTER);
        overflow.setContentDescription("更多附件选项");
        overflow.setOnClickListener(v -> showOverflow());
        header.addView(overflow, new LayoutParams(dp(40), -1));
        addView(header, new LayoutParams(-1, dp(52)));

        notice = new LinearLayout(context);
        notice.setGravity(Gravity.CENTER_VERTICAL);
        notice.setPadding(dp(6), 0, dp(6), 0);
        grant = chip("允许访问相册", () -> onGrant.run());
        system = chip("系统相册", () -> { if (files) onProvider.run(); else onGalleryProvider.run(); });
        notice.addView(grant, wrap());
        notice.addView(system, wrap());
        notice.setVisibility(GONE);
        addView(notice, new LayoutParams(-1, -2));

        status = new TextView(context);
        status.setTextSize(13);
        status.setPadding(dp(16), dp(8), dp(16), dp(8));
        addView(status, new LayoutParams(-1, -2));

        list = new RecyclerView(context);
        list.setItemAnimator(null);
        list.setAdapter(adapter);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(6));
        addView(list, new LayoutParams(-1, 0, 1));

        tabs = new TelegramAttachmentTabsView(context);
        tabs.setCallbacks(() -> setFiles(false), () -> setFiles(true));
        tabs.setLabels("图片", "浏览照片和视频", "文件", "浏览文件与文件夹");
        addView(tabs, new LayoutParams(-1, dp(72)));
        setFiles(false); setColors(colors);
    }

    private TextView label(String text, int size) {
        TextView v = new TextView(getContext());
        v.setText(text); v.setTextSize(size); v.setClickable(true); v.setFocusable(true);
        ripple(v); return v;
    }
    private TextView chip(String text, Runnable action) {
        TextView v = label(text, 14);
        v.setPadding(dp(14), dp(10), dp(14), dp(10));
        v.setOnClickListener(w -> action.run());
        return v;
    }
    private static LayoutParams wrap() { return new LayoutParams(-2, -2); }
    private void ripple(View view) {
        TypedValue out = new TypedValue();
        if (getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true)) {
            view.setBackgroundResource(out.resourceId);
        }
    }

    public void setCallbacks(Consumer<Entry> send, Consumer<Entry> folderEntry, Runnable grant, Runnable provider, Runnable chooseFolder) {
        onSend = send; onFolder = folderEntry; onGrant = grant; onProvider = provider; onChooseFolder = chooseFolder;
    }
    public void setLabels(String gallery, String gallerySummary, String file, String fileSummary) {
        tabs.setLabels(gallery, gallerySummary, file, fileSummary);
    }
    public void setSourceView(View source) { tabs.setSourceView(source); }
    public void setGalleryFallback(Runnable gallery) { onGalleryProvider = gallery; }

    public void setItems(List<Entry> items, boolean hasMediaAccess, boolean loading, String failure) {
        mediaAccess = hasMediaAccess;
        empty = !loading && (failure != null || items.isEmpty());
        String message = loading ? "正在加载…" : failure != null ? failure
                : items.isEmpty() ? (hasMediaAccess ? (files ? "这里没有可分享的文件" : "相册里还没有照片") : "") : "";
        status.setText(message);
        status.setVisibility(message.isEmpty() ? GONE : VISIBLE);
        updateNotice();
        if (entries == items) return;
        entries = items;
        LinkedHashSet<String> names = new LinkedHashSet<>(); names.add("最近照片");
        for (Entry entry : items) if (!entry.album.isEmpty()) names.add(entry.album);
        albumNames = new ArrayList<>(names);
        if (!albumFilter.isEmpty() && !albumNames.contains(albumFilter)) albumFilter = "";
        updateAlbumLabel();
        filter();
    }

    /** Chips only appear when they are actually needed, so the grid keeps its height. */
    private void updateNotice() {
        grant.setVisibility(mediaAccess ? GONE : VISIBLE);
        system.setVisibility(empty ? VISIBLE : GONE);
        system.setText(files ? "系统文件" : "系统相册");
        folder.setVisibility(files ? VISIBLE : GONE);
        notice.setVisibility(mediaAccess && !empty ? GONE : VISIBLE);
    }

    private void updateAlbumLabel() {
        String name = albumFilter.isEmpty() ? (albumNames.isEmpty() ? "最近照片" : albumNames.get(0)) : albumFilter;
        album.setText(name + "  ▾");
    }

    public void setColors(TelegramChatColors colors) {
        boolean changed = this.colors != colors;
        this.colors = colors; applyPanelBackground();
        album.setTextColor(colors.text); send.setTextColor(colors.accent); overflow.setTextColor(colors.secondaryText);
        grant.setTextColor(colors.accent); system.setTextColor(colors.accent); folder.setTextColor(colors.accent);
        status.setTextColor(colors.secondaryText);
        search.setTextColor(colors.text); search.setHintTextColor(colors.secondaryText); tabs.setColors(colors);
        if (changed) {
            android.os.Parcelable position = list.getLayoutManager() == null ? null : list.getLayoutManager().onSaveInstanceState();
            list.setAdapter(null); list.getRecycledViewPool().clear(); list.setAdapter(adapter);
            if (position != null) list.getLayoutManager().onRestoreInstanceState(position);
        }
    }

    /** Rounded top corners drawn by the view itself: interop clip modifiers are unreliable. */
    private void applyPanelBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(colors.menuBackground);
        int radius = dp(20);
        background.setCornerRadii(new float[] { radius, radius, radius, radius, 0, 0, 0, 0 });
        setBackground(background);
        setClipToOutline(true);
    }

    private void setFiles(boolean value) {
        if (files == value && list.getLayoutManager() != null) return;
        files = value; selected = null; updateSend();
        album.setVisibility(files ? GONE : VISIBLE);
        search.setVisibility(files ? VISIBLE : GONE);
        send.setVisibility(files ? GONE : VISIBLE);
        folder.setVisibility(files ? VISIBLE : GONE);
        list.setLayoutManager(files ? new LinearLayoutManager(getContext()) : new GridLayoutManager(getContext(), COLUMNS));
        adapter.notifyDataSetChanged(); filter(); updateNotice();
    }

    private void updateSend() {
        boolean enabled = selected != null;
        send.setEnabled(enabled); send.setAlpha(enabled ? 1f : .4f);
    }

    private void showAlbums() {
        if (albumNames.isEmpty()) return;
        LinearLayout box = menuContainer();
        final PopupWindow[] holder = new PopupWindow[1];
        for (final String name : albumNames) {
            boolean current = name.equals(albumFilter.isEmpty() ? albumNames.get(0) : albumFilter);
            TextView item = menuItem(name, current);
            item.setOnClickListener(v -> {
                albumFilter = name.equals(albumNames.get(0)) ? "" : name;
                selected = null; updateSend(); updateAlbumLabel(); filter();
                if (holder[0] != null) holder[0].dismiss();
            });
            box.addView(item, new LayoutParams(-1, -2));
        }
        holder[0] = openMenu(album, box, Math.max(dp(200), album.getWidth()));
    }

    private void showOverflow() {
        LinearLayout box = menuContainer();
        final PopupWindow[] holder = new PopupWindow[1];
        String provider = files ? "系统文件" : "系统相册";
        TextView openProvider = menuItem(provider, false);
        openProvider.setOnClickListener(v -> {
            if (files) onProvider.run(); else onGalleryProvider.run();
            if (holder[0] != null) holder[0].dismiss();
        });
        box.addView(openProvider, new LayoutParams(-1, -2));
        TextView chooseFolder = menuItem("选择文件夹", false);
        chooseFolder.setOnClickListener(v -> {
            onChooseFolder.run();
            if (holder[0] != null) holder[0].dismiss();
        });
        box.addView(chooseFolder, new LayoutParams(-1, -2));
        holder[0] = openMenu(overflow, box, Math.max(dp(180), dp(200)));
    }

    private LinearLayout menuContainer() {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(colors.menuBackground);
        background.setCornerRadius(dp(14));
        box.setBackground(background);
        box.setClipToOutline(true);
        box.setPadding(0, dp(6), 0, dp(6));
        return box;
    }

    private TextView menuItem(String text, boolean current) {
        TextView item = new TextView(getContext());
        item.setText(text); item.setTextSize(16);
        item.setSingleLine(true); item.setEllipsize(TextUtils.TruncateAt.END);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(18), dp(13), dp(18), dp(13));
        item.setTextColor(current ? colors.accent : colors.text);
        ripple(item);
        return item;
    }

    private PopupWindow openMenu(View anchor, View content, int width) {
        PopupWindow window = new PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setBackgroundDrawable(new ColorDrawable(0));
        window.setOutsideTouchable(true);
        window.setElevation(dp(12));
        window.showAsDropDown(anchor, 0, dp(2));
        return window;
    }

    private void filter() {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (!files && (!entry.mime.startsWith("image/") && !entry.mime.startsWith("video/"))) continue;
            if (!files && !albumFilter.isEmpty() && !albumFilter.equals(entry.album)) continue;
            if (files && !query.isEmpty() && !entry.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            result.add(entry);
        }
        result.sort((a,b) -> {
            if (a.directory != b.directory) return a.directory ? -1 : 1;
            return sortingByName ? a.name.compareToIgnoreCase(b.name) : Long.compare(b.modified, a.modified);
        });
        visible = result; adapter.notifyDataSetChanged();
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        public int getItemViewType(int position) { return files ? 1 : 0; }
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            View view = type == 0 ? new PhotoAttachPhotoCell(parent.getContext(), PickerTheme.colors(colors)) : new SharedDocumentPickerCell(parent.getContext(), PickerTheme.colors(colors));
            view.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(view) {};
        }
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Entry item = visible.get(position);
            if (holder.itemView instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) holder.itemView;
                int width = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
                cell.setTag(item.uri); cell.setItemSize(Math.max(dp(48), width / COLUMNS)); cell.setBitmap(null);
                cell.setDuration(item.duration > 0 ? String.format(Locale.ROOT, "▶ %d:%02d", item.duration / 60000, item.duration / 1000 % 60) : "");
                cell.setChecked(0, selected != null && selected.uri.equals(item.uri), false);
                cell.setContentDescription(item.name);
                WeakReference<PhotoAttachPhotoCell> target = new WeakReference<>(cell);
                Context application = getContext().getApplicationContext();
                THUMBNAILS.execute(() -> {
                    Bitmap thumbnail = null;
                    try {
                        if (Build.VERSION.SDK_INT >= 29) thumbnail = application.getContentResolver().loadThumbnail(Uri.parse(item.uri), new Size(256,256), null);
                        else if (item.mime.startsWith("image/")) {
                            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options(); options.inSampleSize = 8;
                            try (java.io.InputStream stream = application.getContentResolver().openInputStream(Uri.parse(item.uri))) { thumbnail = android.graphics.BitmapFactory.decodeStream(stream, null, options); }
                        }
                    } catch (Exception ignored) { /* Revoked/missing media keeps a selectable placeholder. */ }
                    final Bitmap bitmap = thumbnail;
                    MAIN.post(() -> { PhotoAttachPhotoCell current = target.get(); if (current != null && item.uri.equals(current.getTag())) current.setBitmap(bitmap); });
                });
                cell.setOnClickListener(v -> {
                    boolean choosing = selected == null || !selected.uri.equals(item.uri); selected = choosing ? item : null;
                    for (int i = 0; i < list.getChildCount(); i++) {
                        View child = list.getChildAt(i);
                        if (child instanceof PhotoAttachPhotoCell) ((PhotoAttachPhotoCell)child).setChecked(0, choosing && item.uri.equals(child.getTag()), true);
                    }
                    updateSend();
                });
            } else {
                SharedDocumentPickerCell cell = (SharedDocumentPickerCell)holder.itemView;
                int dot = item.name.lastIndexOf('.');
                cell.setDocument(item.name, item.directory ? "↳" : dot < 0 ? "FILE" : item.name.substring(dot + 1).toUpperCase(Locale.ROOT),
                    item.directory ? "文件夹" : android.text.format.Formatter.formatShortFileSize(getContext(), item.size));
                cell.setOnClickListener(v -> { if (item.directory) onFolder.accept(item); else onSend.accept(item); });
            }
        }
        public int getItemCount() { return visible.size(); }
    }
}
