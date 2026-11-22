/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.monica.telegramchat.upstream.ChatAvatarLayout;
import static com.monica.telegramchat.TelegramUi.dp;

/** Telegram ActionBar glass islands + extracted ChatAvatarContainer layout, without account services. */
public final class TelegramChatHeaderView extends ViewGroup {
    private final TelegramGlassView backGlass, titleGlass, menuGlass;
    private final ImageView back, menu;
    private final ChatAvatarLayout avatar;
    private TelegramMenuPopupWindow menuPopup;

    public TelegramChatHeaderView(Context context) {
        super(context);
        TelegramUi.initialize(context);
        setClipChildren(false);
        backGlass = glass(context); titleGlass = glass(context); menuGlass = glass(context);
        back = button(context, "Back"); menu = button(context, "More options");
        avatar = new ChatAvatarLayout(context);
        addView(avatar);
        setColors(TelegramChatColors.DARK_BLUE);
    }

    private TelegramGlassView glass(Context context) {
        TelegramGlassView view = new TelegramGlassView(context);
        view.setCornerRadius(dp(23));
        view.setChatStyle(false);
        addView(view);
        return view;
    }

    private ImageView button(Context context, String description) {
        ImageView view = new TelegramBounceImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setContentDescription(description);
        view.setBackground(TelegramControlBackground.create());
        view.setClickable(true); view.setFocusable(true);
        addView(view);
        return view;
    }

    public void setTitle(CharSequence text) { avatar.setTitle(text); }
    public void setSubtitle(CharSequence text) { avatar.setSubtitle(text); }
    public void setTyping(boolean typing) { avatar.setTyping(typing); }
    public View getMenuAnchor() { return menu; }
    public void showActionMenu(TelegramPopupLayout content) {
        dismissActionMenu();
        // Collapse before the window is added: PopupWindow can measure/layout/draw the content in the
        // same frame it is attached, so the cascade must already be at its start state by then.
        content.prepareForAnimation();
        menuPopup = new TelegramMenuPopupWindow(content, dp(220));
        menuPopup.showAsDropDown(menu, menu.getWidth() - dp(220), 0);
    }
    public void dismissActionMenu() {
        if (menuPopup != null) { menuPopup.dismissImmediately(); menuPopup = null; }
    }
    @Override protected void onDetachedFromWindow() { dismissActionMenu(); super.onDetachedFromWindow(); }
    public void setAvatar(Drawable drawable) { avatar.setAvatar(drawable); }
    public void setAvatarView(View view) { avatar.setAvatarView(view); }
    public void setCallbacks(Runnable backAction, Runnable infoAction, Runnable menuAction) {
        back.setOnClickListener(v -> { if (backAction != null) backAction.run(); });
        avatar.setOnClickListener(v -> { if (infoAction != null) infoAction.run(); });
        menu.setOnClickListener(v -> { if (menuAction != null) menuAction.run(); });
    }
    public void setColors(TelegramChatColors colors) {
        backGlass.setTintColor(colors.glassTint); titleGlass.setTintColor(colors.glassTint);
        menuGlass.setTintColor(colors.glassTint);
        backGlass.setDark(colors.dark); titleGlass.setDark(colors.dark); menuGlass.setDark(colors.dark);
        back.setImageDrawable(TelegramDrawables.back(colors.text));
        menu.setImageDrawable(TelegramDrawables.tinted(getContext(), R.drawable.telegram_ic_ab_other, colors.text));
        avatar.setColors(colors.text, colors.secondaryText);
    }
    public void setLiquidStyle(boolean enabled) { backGlass.setLiquidStyle(enabled); titleGlass.setLiquidStyle(enabled); menuGlass.setLiquidStyle(enabled); }
    public void setSourceView(View source) {
        backGlass.setSourceView(source); titleGlass.setSourceView(source); menuGlass.setSourceView(source);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        final int width = MeasureSpec.getSize(widthSpec);
        final int height = resolveSize(dp(56), heightSpec);
        final int s = dp(46), p = dp(6);
        int circleSpec = MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY);
        back.measure(circleSpec, circleSpec); menu.measure(circleSpec, circleSpec);
        backGlass.measure(circleSpec, circleSpec); menuGlass.measure(circleSpec, circleSpec);
        final int middleWidth = Math.max(0, width - 2 * s - 4 * p);
        titleGlass.measure(MeasureSpec.makeMeasureSpec(middleWidth, MeasureSpec.EXACTLY), circleSpec);
        avatar.measure(MeasureSpec.makeMeasureSpec(middleWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // ActionBar.dispatchDraw: s=46dp, p=6dp; drawable padding removes p from its outer bounds.
        final int p = dp(6), s = dp(46), t = (getHeight() - s) / 2;
        backGlass.layout(p, t, p + s, t + s); back.layout(p, t, p + s, t + s);
        menuGlass.layout(getWidth() - p - s, t, getWidth() - p, t + s);
        menu.layout(getWidth() - p - s, t, getWidth() - p, t + s);
        final int titleLeft = s + p * 2, titleRight = getWidth() - s - p * 2;
        titleGlass.layout(titleLeft, t, titleRight, t + s);
        avatar.layout(titleLeft, 0, titleRight, getHeight());
    }
}
