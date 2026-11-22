"""Portable official emoji cells/pack and attachment cells. Shared importer context."""
src = JAVA+'messenger/EmojiData.java'
write_source('EmojiData.java', src, (ROOT/src).read_text(encoding='utf-8'),
             ['Complete official Unicode categories, aliases and skin-tone tables retained.'])
src = JAVA+'messenger/EmojiPack.java'
t = (ROOT/src).read_text(encoding='utf-8').replace(
    'ApplicationLoader.applicationContext\n                .getAssets()', 'com.monica.telegramchat.TelegramUi.resources().getAssets()')
write_source('EmojiPack.java', src, t, ['Original EPK3 decoder and bounded root cache unchanged; application assets obtained through TelegramUi.'])
src = 'TMessagesProj/src/main/assets/emoji.pack'
dst = MODULE/'src/main/assets/emoji.pack'
dst.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(ROOT/src, dst)
record(src, dst, ['Original complete emoji.pack, byte-identical; packaged uncompressed for source memory mapping.'])

src = JAVA+'ui/Components/EmojiView.java'
original = (ROOT/src).read_text(encoding='utf-8')
cell = method(original, '    public static class ImageViewEmoji extends ImageView')
parts = [method(cell, signature) for signature in [
    '        public void onMeasure(', '        public void onInitializeAccessibilityNodeInfo(',
    '        public void setPressed(', '        protected void onDraw(']]
t = '''package org.telegram.ui.Components;
import android.animation.*;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import com.monica.telegramchat.TelegramUi;
public final class ImageViewEmoji extends ImageView {
    private float pressedProgress;
    private ValueAnimator backAnimator;
    public ImageViewEmoji(Context context) {
        super(context); TelegramUi.initialize(context);
        setScaleType(ScaleType.CENTER); setClickable(true); setFocusable(true);
    }
'''+ '\n'.join(parts)+'''
    @Override protected void onDetachedFromWindow() {
        if (backAnimator != null) { backAnimator.removeAllListeners(); backAnimator.cancel(); backAnimator = null; }
        pressedProgress = 0; super.onDetachedFromWindow();
    }
}
'''
t = t.replace('AndroidUtilities.screenRefreshRate', 'getDisplay() == null ? 60f : getDisplay().getRefreshRate()')
# Keep the entire screen-refresh term grouped inside the original time calculation.
t = t.replace('1000f / getDisplay() == null ? 60f : getDisplay().getRefreshRate()',
              '1000f / (getDisplay() == null ? 60f : getDisplay().getRefreshRate())')
t = t.replace('Utilities.clamp(pressedProgress, 1f, 0)', 'Math.max(0, Math.min(1f, pressedProgress))')
write_source('ImageViewEmoji.java', src, t, ['Original square cell measurement, accessibility and 350ms overshoot pressed animation retained.',
    'Premium animated emoji/account fields excluded; host supplies original pack bitmap. Refresh rate uses the view Display; clamp uses Math. Detach cancels animator.'])

for name in ['CheckBoxBase', 'CheckBox2']:
    src = JAVA+'ui/Components/'+name+'.java'
    t = (ROOT/src).read_text(encoding='utf-8')
    t = t.replace('import org.telegram.messenger.GenericProvider;', 'import java.util.function.Function;')
    t = t.replace('import org.telegram.ui.ActionBar.Theme;', 'import com.monica.telegramchat.PickerTheme;')
    t = t.replace('import org.telegram.ui.ActionBar.MessageDrawable;\n', '')
    t = t.replace('import org.telegram.messenger.LocaleController;\n', '')
    t = t.replace('Theme.', 'PickerTheme.').replace('GenericProvider<Void, Paint>', 'Function<Void, Paint>')
    if name == 'CheckBox2':
        t = t.replace('    private CheckBoxBase checkBoxBase;', '    private CheckBoxBase checkBoxBase;\n    private final PickerTheme.ResourcesProvider resourcesProvider;')
        t = t.replace('        super(context);', '        super(context);\n        this.resourcesProvider = resourcesProvider;')
        t = t.replace('this(context, sz, null);', 'this(context, sz, PickerTheme.colors(com.monica.telegramchat.TelegramChatColors.DAY));')
        t = t.replace('PickerTheme.getColor(PickerTheme.key_switch2Track)', 'resourcesProvider.getColor(PickerTheme.key_switch2Track)')
    t = t.replace('.provide(null)', '.apply(null)')
    t = t.replace('PickerTheme.getServiceMessageColor()', 'getThemedColor(PickerTheme.key_chat_serviceBackground)')
    t = t.replace('PickerTheme.isCurrentThemeDark()', 'resourcesProvider.isDark()')
    t = t.replace('LocaleController.isRTL', '(parentView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL)')
    t = t.replace('AndroidUtilities.bold()', 'android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)')
    write_source(name+'.java', src, t, ['Original circular selection drawing and animation retained; theme/account utilities supplied explicitly by PickerTheme and java Function. No Telegram service globals.'])

src = JAVA+'ui/Cells/PhotoAttachPhotoCell.java'
original = (ROOT/src).read_text(encoding='utf-8')
size = method(original, '    public void setItemSize(')
measure = method(original[original.index('    public void setItemSize('):], '    protected void onMeasure(').replace('ChatAttachAlertPhotoLayout.GAP', '3')
checked = method(original, '    public void setChecked(')
checked = checked.replace('        setHighQuality(photoEntry != null && photoEntry.isHighQuality() && isChecked());\n', '')
t = '''package org.telegram.ui.Cells;
import android.animation.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import com.monica.telegramchat.TelegramUi;
import com.monica.telegramchat.PickerTheme;
import static com.monica.telegramchat.TelegramUi.dp;
public final class PhotoAttachPhotoCell extends FrameLayout {
    private final FrameLayout container, checkFrame;
    private final ImageView imageView;
    private final TextView videoTextView;
    private final CheckBox2 checkBox;
    private AnimatorSet animator;
    private int itemSize;
    private boolean itemSizeChanged, isVertical, isLast;
    public PhotoAttachPhotoCell(Context context, PickerTheme.ResourcesProvider colors) {
        super(context); TelegramUi.initialize(context);
        container = new FrameLayout(context);
        addView(container, new LayoutParams(dp(80), dp(80)));
        imageView = new ImageView(context); imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        container.addView(imageView, new LayoutParams(-1, -1));
        videoTextView = new TextView(context); videoTextView.setTextColor(0xffffffff);
        videoTextView.setTextSize(12); videoTextView.setShadowLayer(dp(2), 0, 0, 0xff000000);
        LayoutParams time = new LayoutParams(-2, dp(20), Gravity.BOTTOM | Gravity.RIGHT);
        time.rightMargin = dp(6); container.addView(videoTextView, time);
        checkFrame = new FrameLayout(context);
        addView(checkFrame, new LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.RIGHT));
        checkBox = new CheckBox2(context, 24, colors); checkBox.setDrawUnchecked(true);
        addView(checkBox, new LayoutParams(dp(26), dp(26), Gravity.TOP | Gravity.RIGHT));
        setClickable(true); setFocusable(true);
        checkBox.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        imageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        videoTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setBitmap(Bitmap bitmap) { imageView.setImageBitmap(bitmap); }
    public void setDuration(String duration) { videoTextView.setText(duration); }
    public void setPickerColors(int background, int accent) { setBackgroundColor(background); }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info); info.setCheckable(true); info.setChecked(checkBox.isChecked());
    }
    @Override protected void onDetachedFromWindow() {
        if (animator != null) { animator.cancel(); animator = null; }
        super.onDetachedFromWindow();
    }
'''+size+'\n'+measure+'\n'+checked+'\n}\n'
t = t.replace('package org.telegram.ui.Cells;', 'package org.telegram.ui.Components;')
write_source('PhotoAttachPhotoCell.java', src, t, ['Original setItemSize, square grid measurement/3dp gap and 200ms 0.787 selection zoom retained.',
    'ImageReceiver replaced by host-fed ImageView bitmap; original CheckBox2 drawing retained. Single Steam attachment selection; video duration overlay retained. Telegram camera, live photo, spoiler, HQ and paid media excluded.'])

src = JAVA+'ui/Cells/SharedDocumentCell.java'
original = (ROOT/src).read_text(encoding='utf-8')
constructor = method(original, '    public SharedDocumentCell(Context context, int viewType,')
def picker_branch(text):
    # Only the source VIEW_TYPE_PICKER branch, excluding message/download branches.
    start = text.index('if (viewType == VIEW_TYPE_PICKER) {')
    block = method(text[start:], 'if (viewType == VIEW_TYPE_PICKER)')
    return block[block.index('{')+1:-1]
placeholder = 'placeholderImageView = new ImageView(context);'+picker_branch(constructor[constructor.index('placeholderImageView ='):])
ext = constructor[constructor.index('        extTextView ='):constructor.index('        thumbImageView =')]
ext = ext[:ext.index('        if (viewType == VIEW_TYPE_PICKER)')]+picker_branch(ext)
name = constructor[constructor.index('        nameTextView ='):constructor.index('        LinearLayout linearLayout;')]
name += picker_branch(constructor[constructor.index('        LinearLayout linearLayout;'):])
date = constructor[constructor.index('        dateTextView ='):]
date = date[:date.index('        if (viewType == VIEW_TYPE_PICKER)')]+picker_branch(date)
date = date.replace('new AnimatedEmojiSpan.TextViewEmojis(context)', 'new TextView(context)').replace('        NotificationCenter.listenEmojiLoading(dateTextView);\n', '')
statements = placeholder+ext+name+date
statements = statements.replace('getThemedColor(Theme.key_files_iconText)', '0xffffffff')
statements = statements.replace('getThemedColor(Theme.key_windowBackgroundWhiteBlackText)', 'colors.getColor(PickerTheme.key_text)')
statements = statements.replace('getThemedColor(Theme.key_windowBackgroundWhiteGrayText3)', 'colors.getColor(PickerTheme.key_secondaryText)')
statements = statements.replace('LocaleController.isRTL', 'rtl').replace('AndroidUtilities.bold()', 'Typeface.create("sans-serif-medium", Typeface.NORMAL)')
statements = statements.replace('LayoutHelper.createFrame', 'frame').replace('LayoutHelper.MATCH_PARENT', '-1').replace('LayoutHelper.WRAP_CONTENT', '-2')
t = '''package org.telegram.ui.Cells;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import com.monica.telegramchat.PickerTheme;
import com.monica.telegramchat.TelegramUi;
import static com.monica.telegramchat.TelegramUi.dp;
public final class SharedDocumentPickerCell extends FrameLayout {
    private ImageView placeholderImageView;
    private TextView extTextView, nameTextView, dateTextView;
    public SharedDocumentPickerCell(Context context, PickerTheme.ResourcesProvider colors) {
        super(context); TelegramUi.initialize(context);
        boolean rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
'''+statements+'''
        GradientDrawable icon = new GradientDrawable(); icon.setColor(colors.getColor(PickerTheme.key_checkbox)); icon.setCornerRadius(dp(4));
        placeholderImageView.setBackground(icon);
        setClickable(true); setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setDocument(String name, String extension, String detail) {
        nameTextView.setText(name); extTextView.setText(extension); dateTextView.setText(detail);
        setContentDescription(name + ", " + detail);
    }
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(64), MeasureSpec.EXACTLY));
    }
    private static LayoutParams frame(int w, int h, int gravity, float l, float t, float r, float b) {
        LayoutParams p = new LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h), gravity);
        p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p;
    }
}
'''
write_source('SharedDocumentPickerCell.java', src, t, ['Original VIEW_TYPE_PICKER constructor statements for 42dp icon, filename, extension and subtitle plus 64dp measurement extracted.',
    'Original account download/animated status machinery excluded. Host provides name/size/date/URI; explicit palette and RTL configuration replace global Theme/LocaleController.'])
