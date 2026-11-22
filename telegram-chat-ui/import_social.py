"""Source-backed social cells. Executed in import_upstream.py's shared context."""
src = JAVA+'ui/Components/LayoutHelper.java'
t = (ROOT/src).read_text(encoding='utf-8').replace('import org.telegram.messenger.LocaleController;\n', '')
t = t.replace('LocaleController.isRTL', 'TelegramUi.isRTL()')
write_source('LayoutHelper.java', src, t, ['Original layout helpers retained; locale direction comes from the host resources.'])

src = JAVA+'ui/Cells/TextDetailSettingsCell.java'
t = (ROOT/src).read_text(encoding='utf-8')
for imp in ['import org.telegram.messenger.Emoji;\n', 'import org.telegram.messenger.LocaleController;\n', 'import org.telegram.ui.Components.LayoutHelper;\n']:
    t = t.replace(imp, '')
t = t.replace('import org.telegram.ui.ActionBar.Theme;', 'import com.monica.telegramchat.SocialTheme;')
t = t.replace('public TextDetailSettingsCell(Context context)', 'public TextDetailSettingsCell(Context context, SocialTheme theme)')
t = t.replace('        super(context);', '        super(context);\n        TelegramUi.initialize(context);\n        this.theme = theme;')
t = t.replace('    private TextView textView;', '    private final SocialTheme theme;\n    private TextView textView;')
t = t.replace('Theme.getColor(Theme.key_', 'theme.getColor(SocialTheme.key_').replace('Theme.dividerPaint', 'theme.dividerPaint')
t = t.replace('LocaleController.isRTL', '(getLayoutDirection() == LAYOUT_DIRECTION_RTL)')
t = t.replace('Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false)', 'text')
t = t.replace('        textView.invalidate();', '        if (textView != null) textView.invalidate();')
write_source('TextDetailSettingsCell.java', src, t, ['Complete original cell constructor, measurement, multiline, value/icon setters and divider rendering retained.', 'Theme is per-instance SocialTheme; Unicode uses platform spans. Null guard during View construction.'])

src = JAVA+'ui/Cells/HeaderCell.java'
original = (ROOT/src).read_text(encoding='utf-8')
ctor = method(original, '    public HeaderCell(Context context, int textColorKey, int padding, int topMargin, int bottomMargin, boolean text2, boolean animated,')
body = ctor[ctor.index('        } else {') + len('        } else {'):ctor.index('\n        if (text2)')]
body = body.rsplit('        }', 1)[0]
t = '''package org.telegram.ui.Cells;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.*;
import androidx.core.view.ViewCompat;
import com.monica.telegramchat.TelegramUi;
import com.monica.telegramchat.SocialTheme;
public final class HeaderCell extends FrameLayout {
    private final TextView textView;
    public HeaderCell(Context context, SocialTheme theme) {
        super(context); TelegramUi.initialize(context);
        final int height = 40, topMargin = 7, padding = 20, bottomMargin = 0;
        final boolean text2 = false;
'''+body+'''
        ViewCompat.setAccessibilityHeading(this, true);
    }
    public void setText(CharSequence text) { textView.setText(text); }
'''+method(original, '    protected void onMeasure(')+'\n}\n'
t = t.replace('LocaleController.isRTL', '(getLayoutDirection() == LAYOUT_DIRECTION_RTL)')
t = t.replace('AndroidUtilities.bold()', 'android.graphics.Typeface.create("sans-serif-medium", 0)')
t = t.replace('getThemedColor(textColorKey)', 'theme.accent').replace('textView.setTag(textColorKey);', '')
write_source('HeaderCell.java', src, t, ['Original nonanimated, single-title constructor branch and onMeasure retained. AnimatedTextView/secondary-title branches excluded; per-instance colors.'])

src = JAVA+'ui/Cells/UserCell.java'
original = (ROOT/src).read_text(encoding='utf-8')
ctor = method(original, '    public UserCell(Context context, int padding, int checkbox, boolean admin, boolean needAddButton,')
avatar = ctor[ctor.index('        avatarImageView.setRoundRadius'):ctor.index('        if (checkbox == 2)')]
avatar = re.sub(r'        (botVerification|emojiStatus) = [^\n]+\n', '', avatar)
avatar = avatar.replace('SimpleTextView', 'TextView').replace('.setTextSize(16)', '.setTextSize(16)').replace('.setTextSize(15)', '.setTextSize(15)')
avatar = avatar.replace('nameTextView.setTextSize(16);', 'nameTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);\n        nameTextView.setIncludeFontPadding(true); nameTextView.setSingleLine(); nameTextView.setEllipsize(android.text.TextUtils.TruncateAt.END);')
avatar = avatar.replace('statusTextView.setTextSize(15);', 'statusTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);\n        statusTextView.setIncludeFontPadding(true); statusTextView.setSingleLine(); statusTextView.setEllipsize(android.text.TextUtils.TruncateAt.END);')
avatar = avatar.replace('LayoutHelper.MATCH_PARENT, 20,', 'LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,')
# Platform TextView measures fallback-font padding differently from SimpleTextView.
# Retain the original minimum while allowing the host's measured labels to expand it.
measure = '''    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Platform TextView needs its measured font/fallback padding, unlike SimpleTextView.
        // A fixed 20dp label clips the descent at fractional display densities.
        int width = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
        super.onMeasure(width, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        LayoutParams title = (LayoutParams) nameTextView.getLayoutParams();
        LayoutParams status = (LayoutParams) statusTextView.getLayoutParams();
        status.topMargin = title.topMargin + nameTextView.getMeasuredHeight() + dp(2);
        int required = Math.max(dp(58), status.topMargin + statusTextView.getMeasuredHeight() + dp(6));
        super.onMeasure(width, MeasureSpec.makeMeasureSpec(
            resolveSize(required + (needDivider ? 1 : 0), heightMeasureSpec), MeasureSpec.EXACTLY));
    }'''
draw = method(original[original.index('    public void setAvatarPadding('):], '    protected void onDraw(')
t = '''package org.telegram.ui.Cells;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.monica.telegramchat.*;
import static com.monica.telegramchat.TelegramUi.dp;
public final class UserCell extends FrameLayout {
    private final SocialTheme theme;
    private final SteamAvatarView avatarImageView;
    private final TextView nameTextView, statusTextView;
    private final ImageView imageView;
    private boolean needDivider;
    private float textScale = 1f;
    public UserCell(Context context, SocialTheme theme) {
        super(context); TelegramUi.initialize(context); this.theme = theme;
        final int padding = 0, checkbox = 0, additionalPadding = 0;
        avatarImageView = new SteamAvatarView(context);
'''+avatar+'''
        setFocusable(true);
        setClickable(true);
        setBackground(theme.selector());
    }
    public void bind(String name, String status, boolean online, boolean divider) {
        nameTextView.setText(name); statusTextView.setText(status);
        statusTextView.setTextColor(online ? theme.accent : theme.secondary);
        avatarImageView.setInitials(name, theme.accent, theme.accentText);
        needDivider = divider; setWillNotDraw(!divider);
        setContentDescription(name + ", " + status);
    }
    public void setAvatar(android.graphics.Bitmap bitmap) { avatarImageView.setBitmap(bitmap); }
    public void setTextScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0f) scale = 1f;
        if (textScale == scale) return;
        textScale = scale;
        nameTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dp(16) * scale);
        statusTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dp(15) * scale);
        requestLayout();
    }
'''+measure+'\n'+draw+'\n}\n'
t = t.replace('LocaleController.isRTL', '(getLayoutDirection() == LAYOUT_DIRECTION_RTL)')
t = t.replace('AndroidUtilities.bold()', 'android.graphics.Typeface.create("sans-serif-medium", 0)')
t = t.replace('Theme.getColor(Theme.key_', 'theme.getColor(SocialTheme.key_').replace(', resourcesProvider)', ')')
t = t.replace('Theme.dividerPaint', 'theme.dividerPaint')
write_source('UserCell.java', src, t, ['Extracted actual avatar/name/status/icon constructor statements, 58dp onMeasure and inset divider onDraw.', 'Account-bound setData/updates/stories/premium/check/admin/add branches excluded. Steam bind supplies text/status/bitmap; SimpleTextView and ImageReceiver are replaced with platform TextView and SteamAvatarView.', 'Host TextView adaptation measures font/fallback padding, places status below the measured title and expands above the original 58dp minimum to avoid clipped descent at fractional display density. Telegram style applies explicit host font scale; other styles retain their text size.'])

# DialogCell is account-heavy. Keep its custom-dialog model, time/name/count layout
# statements and text geometry; excluded account paths are recorded explicitly.
src = JAVA+'ui/Cells/DialogCell.java'
original = (ROOT/src).read_text(encoding='utf-8')
model = method(original, '    public static class CustomDialog')
time = original[original.index('        int timeWidth;\n', original.index('    public void buildLayout()')):original.index('        int timeLeftOffset = 0;', original.index('    public void buildLayout()'))]
name = original[original.index('        if (!LocaleController.isRTL) {\n            nameWidth ='):original.index('        if (drawNameLock) {\n            nameWidth -=')]
count = original[original.index('                countWidth = Math.max(dp(BADGE_TEXT_MIN_WIDTH)'):original.index('                drawCount = true;', original.index('                countWidth = Math.max(dp(BADGE_TEXT_MIN_WIDTH)'))]
t = '''package org.telegram.ui.Cells;
import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.*;
import android.widget.FrameLayout;
import com.monica.telegramchat.*;
import static com.monica.telegramchat.TelegramUi.dp;
public final class DialogCell extends FrameLayout {
'''+model+'''
    private static final float BADGE_TEXT_PADDING = 6.333f, BADGE_TEXT_MIN_WIDTH = 8f, BADGE_GAP = 17f, BADGE_MARGIN = 15.666f;
    private final SocialTheme theme;
    private final SteamAvatarView avatar;
    private final TextPaint namePaint = new TextPaint(3), messagePaint = new TextPaint(3), timePaint = new TextPaint(3), countPaint = new TextPaint(3);
    private StaticLayout nameLayout, messageLayout, timeLayout, countLayout;
    private int nameLeft, nameWidth, timeLeft, countWidth, countLeft, messageWidth, messageLeft, typingLeft, buttonLeft, messageNameLeft;
    private final int messagePaddingStart = 72;
    private CustomDialog customDialog;
    private String formattedTime = "";
    private float textScale = 1f;
    public DialogCell(Context context, SocialTheme theme) {
        super(context); TelegramUi.initialize(context); this.theme = theme;
        setWillNotDraw(false); setFocusable(true); setClickable(true); setBackground(theme.selector());
        namePaint.setTextSize(dp(16)); namePaint.setTypeface(Typeface.create("sans-serif-medium", 0)); namePaint.setColor(theme.text);
        messagePaint.setTextSize(dp(15)); messagePaint.setColor(theme.secondary);
        timePaint.setTextSize(dp(12)); timePaint.setColor(theme.secondary);
        countPaint.setTextSize(dp(13)); countPaint.setTypeface(Typeface.create("sans-serif-medium", 0)); countPaint.setColor(theme.accentText);
        avatar = new SteamAvatarView(context); avatar.setRoundRadius(dp(28));
        addView(avatar, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.START, 10, 10, 10, 0));
    }
    public void setDialog(CustomDialog dialog, String time) {
        customDialog = dialog; formattedTime = time; avatar.setInitials(dialog.name, theme.accent, theme.accentText);
        // The host supplies the fully localized description after binding Steam data.
        buildLayout(); requestLayout(); invalidate();
    }
    /** Host-only adaptation: previous designs pass 1; Telegram mode passes the user's font scale. */
    public void setTextScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0f) scale = 1f;
        if (textScale == scale) return;
        textScale = scale;
        namePaint.setTextSize(dp(16) * scale); messagePaint.setTextSize(dp(15) * scale);
        timePaint.setTextSize(dp(12) * scale); countPaint.setTextSize(dp(13) * scale);
        buildLayout(); requestLayout(); invalidate();
    }
    public void setAvatar(Bitmap bitmap) { avatar.setBitmap(bitmap); }
    private TextPaint getTimeTextPaint() { return timePaint; }
    private boolean getIsPinned() { return customDialog != null && customDialog.pinned; }
    @Override protected void onMeasure(int w, int h) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(dp(78), MeasureSpec.EXACTLY)); buildLayout();
        if (textScale > 1f) {
            int desired = dp(78);
            if (messageLayout != null) desired = Math.max(desired, messageTop() + messageLayout.getHeight() + dp(10));
            if (countLayout != null) desired = Math.max(desired, badgeTop() + badgeHeight() + dp(10));
            if (nameLayout != null) desired = Math.max(desired, dp(10) + nameLayout.getHeight() + dp(10));
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(resolveSize(desired, h), MeasureSpec.EXACTLY));
        }
    }
    private int messageTop() {
        return textScale <= 1f || nameLayout == null ? dp(32) : Math.max(dp(32), dp(10) + nameLayout.getHeight() + dp(4));
    }
    private int badgeTop() { return messageTop() + dp(13); }
    private int badgeHeight() {
        return textScale <= 1f || countLayout == null ? dp(23) : Math.max(dp(23), countLayout.getHeight() + dp(6));
    }
    private StaticLayout oneLine(CharSequence text, TextPaint paint, int width) {
        width = Math.max(dp(12), width);
        return new StaticLayout(TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END), paint, width, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
    }
    public void buildLayout() {
        if (customDialog == null || getMeasuredWidth() <= 0) return;
        boolean drawTime = !formattedTime.isEmpty(); String timeString = formattedTime;
        nameLeft = dp(isRtl() ? 22 : messagePaddingStart + 6);
'''+time+name+'''
        nameLayout = oneLine(customDialog.name, namePaint, nameWidth);
        messageLeft = dp(isRtl() ? 16 : messagePaddingStart + 6);
        messageWidth = getMeasuredWidth() - dp(messagePaddingStart + 21);
        countLayout = null; countWidth = 0;
        String countString = customDialog.unread_count > 0 ? String.valueOf(customDialog.unread_count) : null;
        if (countString != null) {
'''+count+'''
        }
        messageWidth = Math.max(dp(12), messageWidth);
        messageLayout = StaticLayout.Builder.obtain(customDialog.message, 0, customDialog.message.length(), messagePaint, messageWidth)
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build();
    }
    private boolean isRtl() { return getLayoutDirection() == LAYOUT_DIRECTION_RTL; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); if (nameLayout == null) return;
        drawLayout(canvas, nameLayout, nameLeft, dp(10));
        drawLayout(canvas, messageLayout, messageLeft, messageTop());
        drawLayout(canvas, timeLayout, timeLeft, dp(13));
        if (countLayout != null) {
            Paint badge = theme.badgePaint;
            RectF rect = new RectF(countLeft, badgeTop(), countLeft + countWidth + dp(BADGE_TEXT_PADDING * 2), badgeTop() + badgeHeight());
            canvas.drawRoundRect(rect, badgeHeight() / 2f, badgeHeight() / 2f, badge);
            float countTop = textScale <= 1f ? dp(48) : badgeTop() + (badgeHeight() - countLayout.getHeight()) / 2f;
            drawLayout(canvas, countLayout, countLeft + dp(BADGE_TEXT_PADDING), countTop);
        }
        if (getIsPinned()) {
            android.graphics.drawable.Drawable pin = getResources().getDrawable(com.monica.telegramchat.R.drawable.telegram_msg_pin, null).mutate();
            pin.setTint(theme.secondary);
            int left = isRtl() ? dp(15) : getWidth() - dp(31);
            pin.setBounds(left, dp(12), left + dp(16), dp(28)); pin.draw(canvas);
        }
    }
    private void drawLayout(Canvas canvas, StaticLayout layout, float x, float y) {
        if (layout == null) return; canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore();
    }
}
'''
t = t.replace('LocaleController.isRTL', 'isRtl()').replace('Theme.dialogs_countTextPaint2', 'countPaint')
t = t.replace('timeLayout = new StaticLayout(timeString, tp, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);', '''if (textScale > 1f) {
                // Keep a readable name slot when localized dates grow at accessibility sizes.
                timeWidth = Math.min(timeWidth, Math.max(dp(24), (getMeasuredWidth() - dp(messagePaddingStart + 35)) / 2));
                timeLayout = oneLine(timeString, tp, timeWidth);
            } else {
                timeLayout = new StaticLayout(timeString, tp, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }''')
write_source('DialogCell.java', src, t, ['Portable CustomDialog subset: original model and time/name/count layout statements and baseline three-line text/badge geometry.', 'Full account-bound DialogCell is not imported: Telegram model updates, send checks, stories, spoiler/premium layers, swipe/archive/topic/transition paths excluded. StaticLayout text rendering and host bitmap avatar replace account renderers; host formats dates and localized accessibility descriptions.', 'Host accessibility adaptation: explicit text scale, measured multiline/badge height and bounded time width expand Telegram-style rows above the original 78dp minimum. Scale 1 preserves the previous fixed geometry; the full upstream class does not provide this host adaptation.'])

src = JAVA+'ui/Components/Text.java'
original = (ROOT/src).read_text(encoding='utf-8')
signatures = ['    public Text(CharSequence text, float textSizeDp, Typeface typeface)',
    '    public void setText(CharSequence text)', '    public Text multiline(', '    public boolean isMultiline(',
    '    public Text align(', '    public Text setMaxWidth(', '    public int getLineCount(',
    '    public float getWidth(', '    public float getHeight(', '    public CharSequence getText(',
    '    public void draw(Canvas canvas, float x, float cy, int color, float alpha)']
parts = '\n'.join(method(original, s) for s in signatures)
parts = replace_method(parts, '        if (parentView != null && parentView.isAttachedToWindow())', '')
parts = parts.replace('AndroidUtilities.replaceNewLines(text)', 'text.toString().replace("\\n", " ")')
t = '''package org.telegram.ui.Components;
import android.graphics.*;
import android.os.Build;
import android.text.*;
import static com.monica.telegramchat.TelegramUi.dp;
public final class Text {
    public final TextPaint paint;
    private StaticLayout layout;
    private float width, left, maxWidth = 9999, lineSpacingAdd, ellipsizeWidth = -1;
    private int maxLines = 1;
    private Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
    private boolean doNotSave;
'''+parts+'''
    public void draw(Canvas canvas) { canvas.save(); canvas.translate(-left, 0); layout.draw(canvas); canvas.restore(); }
}
'''
write_source('Text.java', src, t, ['Original StaticLayout text construction, multiline, width/alignment, alpha/color/position draw methods retained. Animated premium emoji, shader ellipsis and theme services excluded.'])

src = JAVA+'ui/Components/ProfileActionsView.java'
original = (ROOT/src).read_text(encoding='utf-8')
draw = method(original, '    protected void onDraw(')
while '        if (renderNode != null)' in draw:
    draw = replace_method(draw, '        if (renderNode != null)')
draw = replace_method(draw, '                    if (SharedConfig.shadowsInSections', '                    paint.setShadowLayer(0, 0, 0, 0);')
# The source's corresponding else branch belongs to the omitted preference switch.
draw = draw.replace(' else {\n                        paint.setShadowLayer(0, 0, 0, 0);\n                    }', '')
draw = draw.replace('        drawRenderNode(canvas);', '')
drawAction = method(original, '    private void drawAction(Canvas canvas, Action action, float fraction, float alpha)')
animated = method(drawAction, '            if (action.drawableAnimated != null)')
drawAction = drawAction.replace(animated+' else {', '{')
drawAction = drawAction.replace('        drawLoading(canvas, action, alpha);', '')
touch = method(original, '    public boolean onTouchEvent(')
touch = replace_method(touch, '                    if (hit.supportsLoading')
touch = replace_method(touch, '                    if (hit.supportsAnimate')
action = method(original, '    private class Action')
action = replace_method(action, '        public Action(ActionButton button)', '''        public Action(int key, CharSequence text, int filled, int outline) {
            this.key = key; setText(text);
            drawableFilled = getResources().getDrawable(filled, null).mutate();
            drawableOutline = getResources().getDrawable(outline, null).mutate();
        }''')
for sig in ['        public void delete()', '        public void updateDrawable(boolean animated,',
    '        public void update(boolean animated,', '        public void update(ActionButton button)',
    '        public void updateDrawable(@RawRes']:
    action = replace_method(action, sig)
action = action.replace('        @Deprecated\n', '').replace('        @SuppressLint("UseCompatLoadingForDrawables")\n', '')
action = replace_method(action, '            if (drawableAnimated != null)')
action = action.replace('        private RLottieDrawable drawableAnimated;\n', '').replace('        LoadingDrawable loadingDrawable;\n', '')
ctor = method(original, '    public ProfileActionsView(Context context, int targetHeight)')
ctor = ctor.replace('        super(context);', '        super(context); com.monica.telegramchat.TelegramUi.initialize(context);\n        currentHeight = targetHeight;')
accessibility = method(original, '    public AccessibilityNodeProvider getAccessibilityNodeProvider()')
methods = '\n'.join(method(original, sig) for sig in [
    '    public void setOnActionClickListener(', '    protected void onMeasure(', '    private float getItemWidth(',
    '    private void updateBounds(', '    private void drawActionDrawable(', '    public float getRoundRadius('])
t = '''package org.telegram.ui.Components;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.text.Layout;
import android.view.*;
import android.view.accessibility.*;
import androidx.annotation.*;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import com.monica.telegramchat.TelegramUi;
import java.util.*;
import static com.monica.telegramchat.TelegramUi.dp;
import static com.monica.telegramchat.TelegramUi.dpf2;
public final class ProfileActionsView extends View {
    public static final int KEY_MESSAGE = 0, KEY_CALL = 1, KEY_VIDEO = 2, KEY_GIFT = 3, KEY_DISCUSS = 4;
    private static final int MODE_USER = 0, MODE_CHANNEL = 1;
    private final int mode = MODE_USER;
    private final Paint paint = new Paint(3), shaderPaint = new Paint(3);
    private final Matrix matrix = new Matrix();
    private RadialGradient radialGradient;
    private final RectF scratch = new RectF();
    private float parentExpanded, clipHeight = -1, currentHeight;
    private int targetHeight, activeCount;
    private final float xpadding, ypadding, top, textPadding;
    private boolean ignoreRect, isOpeningLayout = true, isAnimatingCallAction;
    private final ArrayList<Action> actions = new ArrayList<>();
    private Action firstAction, lastAction, hit;
    private float downX, downY;
    private long downTime;
    private int lastColorFilterColor;
    private ColorFilter lastColorFilter;
    private int actionColor;
    private int actionTextColor;
    public interface OnActionClickListener { void onClick(int key, float x, float y); }
    private OnActionClickListener onActionClickListener;
    private AccessibilityNodeProvider accessibilityNodeProvider;
'''+ctor+methods+draw+drawAction+touch+action+accessibility+'''
    private boolean isButtonColorLight() { return ColorUtils.calculateLuminance(actionColor) > .48; }
    public void setActionColor(int color) { actionColor = color; paint.setColor(color); invalidate(); }
    public void setActionTextColor(int color) { actionTextColor = color; invalidate(); }
    public void setActions(String[] titles, int[] filled, int[] outline) {
        boolean same = actions.size() == titles.length;
        if (same) { for (int i=0;i<titles.length;i++) if (!actions.get(i).text.getText().toString().equals(titles[i])) same=false; }
        if (same) return;
        actions.clear(); for (int i=0;i<titles.length;i++) actions.add(new Action(i,titles[i],filled[i],outline[i]));
        activeCount=actions.size(); invalidate();
    }
    @Override protected void onDetachedFromWindow() {
        if (hit != null) hit.bounce.setPressed(false); hit = null; super.onDetachedFromWindow();
    }
}
'''
t = t.replace('AndroidUtilities.rectTmp', 'scratch').replace('Utilities.clamp01(', 'clamp(')
t = t.replace('AndroidUtilities.bold()', 'Typeface.create("sans-serif-medium", 0)')
t = t.replace('ColorUtils.blendARGB(Color.BLACK, Color.WHITE, useFilledWhiteIcon)', 'actionTextColor')
t = t.replace('    private boolean isButtonColorLight()', '    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }\n    private boolean isButtonColorLight()')
write_source('ProfileActionsView.java', src, t, ['Original action geometry, layout/drawing, touch handling, accessibility provider, ButtonBounce and position animations retained.', 'Telegram account action registry replaced with host labels/icons/callbacks. Avatar RenderNode blur, loading/lottie and preference-shadow branches excluded; per-instance solid action color.'])

for folder in ['drawable', 'drawable-mdpi', 'drawable-hdpi', 'drawable-xhdpi', 'drawable-xxhdpi']:
    for name in ['filled_profile_message_24.xml', 'outline_profile_message_24.xml', 'filled_profile_edit_24.xml', 'outline_profile_edit_24.xml', 'msg_copy.webp', 'msg_copy_filled.webp', 'msg_pin.webp', 'msg_link.webp']:
        src = 'TMessagesProj/src/main/res/'+folder+'/'+name
        if (ROOT/src).exists():
            dst = MODULE/'src/main/res'/folder/('telegram_'+name)
            dst.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(ROOT/src, dst)
            record(src,dst,['Original icon; renamed with telegram_ prefix, bytes unchanged.'])
