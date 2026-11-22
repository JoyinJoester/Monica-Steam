/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import android.content.Context;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import com.monica.telegramchat.upstream.ComposerActionAnimator;
import com.monica.telegramchat.upstream.CubicBezierInterpolator;
import static com.monica.telegramchat.TelegramUi.dp;

/** Portable native ChatActivityEnterView row; Steam owns the actual send and popup models. */
public final class TelegramChatComposerView extends ViewGroup {
    public interface TextChangedListener { void onTextChanged(String text); }
    private final TelegramGlassView glass;
    private final TelegramEditText input;
    private final ImageView emoji, bot, attach, action, send;
    private AnimatorSet actionAnimation;
    private boolean colorsInitialized;
    private TextChangedListener textListener;
    private Runnable sendAction, emojiAction, attachAction, menuAction;
    private TelegramChatColors colors = TelegramChatColors.DARK_BLUE;
    private boolean sendEnabled = true, botVisible = true, hasText;
    private boolean editingFromHost, sendContent;

    public TelegramChatComposerView(Context context) {
        super(context);
        TelegramUi.initialize(context);
        setClipChildren(false);
        glass = new TelegramGlassView(context);
        glass.setCornerRadius(dp(22)); glass.setChatStyle(true); addView(glass);
        input = new TelegramEditText(context);
        // ChatActivityEnterView.createMessageEditText (5807-5824), retained native EditText setup.
        input.setIncludeFontPadding(false);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_SEND);
        input.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false); input.setMaxLines(6);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setGravity(Gravity.BOTTOM);
        input.setPadding(0, dp(9), 0, dp(10));
        input.setBackground(null);
        if (Build.VERSION.SDK_INT >= 28) input.setFallbackLineSpacing(false);
        addView(input);
        emoji = button(context, "Emoji and stickers");
        bot = button(context, "Message options");
        attach = button(context, "Attach");
        action = button(context, "More options");
        send = button(context, "Send");
        emoji.setOnClickListener(v -> { if (emojiAction != null) emojiAction.run(); });
        bot.setOnClickListener(v -> { if (menuAction != null) menuAction.run(); });
        attach.setOnClickListener(v -> { if (attachAction != null) attachAction.run(); });
        action.setOnClickListener(v -> { if (!hasText && menuAction != null) menuAction.run(); });
        send.setOnClickListener(v -> { if (hasText && sendEnabled && sendAction != null) sendAction.run(); });
        input.setOnEditorActionListener((v, id, event) -> {
            if (id == EditorInfo.IME_ACTION_SEND) {
                if (hasText && sendEnabled && sendAction != null) sendAction.run();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateAction(); requestLayout();
                if (!editingFromHost && textListener != null) textListener.onTextChanged(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        setColors(colors);
    }

    private ImageView button(Context context, String description) {
        ImageView button = new TelegramBounceImageView(context);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setContentDescription(description);
        button.setBackground(TelegramControlBackground.create());
        button.setClickable(true); button.setFocusable(true);
        addView(button);
        return button;
    }

    public void setCallbacks(TextChangedListener textChanged, Runnable send, Runnable emoji,
                             Runnable attach, Runnable menu) {
        boolean menuChanged = (menuAction == null) != (menu == null);
        textListener = textChanged; sendAction = send; emojiAction = emoji;
        attachAction = attach; menuAction = menu;
        if (menuChanged) { updateAction(); requestLayout(); }
    }

    public TelegramEditText getEditText() { return input; }
    public void setLiquidStyle(boolean enabled) { glass.setLiquidStyle(enabled); }
    public void setSourceView(View source) { glass.setSourceView(source); }
    public void setHint(CharSequence hint) { if (!TextUtils.equals(input.getHint(), hint)) input.setHint(hint); }
    public void setBotVisible(boolean visible) {
        if (botVisible != visible) { botVisible = visible; bot.setVisibility(visible ? VISIBLE : GONE); requestLayout(); }
    }
    public void setSendEnabled(boolean enabled) { if (sendEnabled != enabled) { sendEnabled = enabled; updateAction(); } }
    /** Steam may have a game-share draft with no typed caption. */
    public void setSendContent(boolean value) { if (sendContent != value) { sendContent = value; updateAction(); } }

    /** A Compose state echo must not reset composing spans, selection, scroll or focus. */
    public void setTextIfChanged(CharSequence text) {
        CharSequence value = text == null ? "" : text;
        if (TextUtils.equals(input.getText(), value)) return;
        int start = input.getSelectionStart(), end = input.getSelectionEnd();
        boolean atEnd = start == input.length() && end == input.length();
        editingFromHost = true;
        try {
            input.setText(value);
            if (atEnd || start < 0 || end < 0) input.setSelection(input.length());
            else input.setSelection(Math.min(start, input.length()), Math.min(end, input.length()));
        } finally { editingFromHost = false; }
    }

    public void setColors(TelegramChatColors colors) {
        if (colorsInitialized && this.colors == colors) return;
        this.colors = colors;
        colorsInitialized = true;
        glass.setTintColor(colors.composerTint); glass.setDark(colors.dark);
        input.setTextColor(colors.text); input.setHintTextColor(colors.hint);
        input.setCursorColor(colors.accent); input.setHighlightColor((colors.accent & 0xffffff) | 0x55000000);
        emoji.setImageDrawable(TelegramDrawables.tinted(getContext(), R.drawable.telegram_input_smile, colors.icon));
        bot.setImageDrawable(TelegramDrawables.tinted(getContext(), R.drawable.telegram_input_bot2, colors.icon));
        attach.setImageDrawable(TelegramDrawables.tinted(getContext(), R.drawable.telegram_msg_input_attach2, colors.icon));
        action.setImageDrawable(TelegramDrawables.tinted(getContext(), R.drawable.telegram_ic_ab_other, colors.icon));
        send.setImageDrawable(TelegramDrawables.send(getContext(), colors.accent));
        updateAction();
    }

    private void updateAction() {
        boolean previous = hasText;
        hasText = sendContent || input.getText() != null && !input.getText().toString().trim().isEmpty();
        send.setEnabled(hasText && sendEnabled);
        action.setEnabled(!hasText && menuAction != null);
        bot.setVisibility(botVisible && menuAction != null && !hasText ? VISIBLE : GONE);
        if (previous != hasText) {
            animateAction(isAttachedToWindow() && isLaidOut());
            requestLayout();
        } else if (actionAnimation == null) settleAction();
    }

    private void settleAction() {
        boolean showMenu = !hasText && menuAction != null;
        send.setVisibility(hasText ? VISIBLE : INVISIBLE);
        action.setVisibility(showMenu ? VISIBLE : INVISIBLE);
        send.setAlpha(hasText ? 1f : 0f); action.setAlpha(showMenu ? 1f : 0f);
        send.setScaleX(hasText ? 1f : .1f); send.setScaleY(send.getScaleX());
        action.setScaleX(showMenu ? 1f : .1f); action.setScaleY(action.getScaleX());
        requestLayout();
    }

    private void animateAction(boolean animated) {
        if (actionAnimation != null) {
            AnimatorSet previous = actionAnimation; actionAnimation = null; previous.cancel();
        }
        if (!animated) { settleAction(); return; }
        send.setVisibility(VISIBLE); action.setVisibility(menuAction != null ? VISIBLE : INVISIBLE);
        AnimatorSet animation = new AnimatorSet();
        actionAnimation = animation;
        animation.playTogether(ComposerActionAnimator.animate(send, hasText),
                ComposerActionAnimator.animate(action, !hasText && menuAction != null));
        // ChatActivityEnterView.checkSendButton uses 220ms quintic entry / 150ms return.
        animation.setDuration(hasText ? 220 : 150);
        if (hasText) animation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animation.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animator) {
                if (actionAnimation == animator) { actionAnimation = null; settleAction(); }
            }
        });
        animation.start();
    }

    @Override protected void onDetachedFromWindow() {
        animateAction(false);
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        final int width = MeasureSpec.getSize(widthSpec);
        final int controls = (bot.getVisibility() == VISIBLE ? 1 : 0) + 1 + (hasActionSlot() ? 1 : 0);
        final int textWidth = Math.max(0, width - dp(7 + 52 + 7 + controls * 44));
        input.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(160), MeasureSpec.AT_MOST));
        final int islandHeight = Math.max(dp(44), input.getMeasuredHeight() + dp(1.5f));
        // ChatInputViewsContainer.INPUT_BUBBLE_BOTTOM=9; outer top clearance=9 in EnterView.
        final int height = resolveSize(islandHeight + dp(18), heightSpec);
        final int controlSpec = MeasureSpec.makeMeasureSpec(dp(44), MeasureSpec.EXACTLY);
        emoji.measure(controlSpec, controlSpec); bot.measure(controlSpec, controlSpec);
        attach.measure(controlSpec, controlSpec); action.measure(controlSpec, controlSpec); send.measure(controlSpec, controlSpec);
        glass.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width - dp(14)), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(dp(44), height - dp(18)), MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int islandBottom = getHeight() - dp(9), controlTop = islandBottom - dp(44);
        final int islandLeft = dp(7), islandRight = getWidth() - dp(7);
        glass.layout(islandLeft, dp(9), islandRight, islandBottom);
        emoji.layout(islandLeft + dp(2), controlTop, islandLeft + dp(46), islandBottom);
        int x = islandRight - dp(44);
        action.layout(x, controlTop, x + dp(44), islandBottom);
        send.layout(x, controlTop, x + dp(44), islandBottom);
        if (hasActionSlot()) x -= dp(44);
        attach.layout(x, controlTop, x + dp(44), islandBottom);
        if (bot.getVisibility() == VISIBLE) {
            x -= dp(44); bot.layout(x, controlTop, x + dp(44), islandBottom);
        }
        final int inputLeft = islandLeft + dp(52), inputBottom = islandBottom - dp(1.5f);
        input.layout(inputLeft, inputBottom - input.getMeasuredHeight(), x, inputBottom);
    }

    private boolean hasActionSlot() { return hasText || menuAction != null || actionAnimation != null; }
}
