"""Additional reproducible source extraction, executed by import_upstream.py."""

# Exact native static text layout (including Telegram's width overflow workaround).
src = JAVA+'messenger/MessageObject.java'
original = (ROOT/src).read_text(encoding='utf-8')
t = '''package org.telegram.ui.Components;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
public final class MessageTextLayout {
'''+method(original, '    public static StaticLayout makeStaticLayout(CharSequence text_, TextPaint paint, int width, float lineSpacingMult, float lineSpacingAdd, boolean dontIncludePad, Layout.Alignment alignment)')+'\n}\n'
write_source('MessageTextLayout.java', src, t, ['Full makeStaticLayout overload extracted unchanged, including high-quality layout, no hyphenation, no fallback line spacing, overflow fallback to simple break strategy.'])

src = JAVA+'ui/Components/MsgClockDrawable.java'
write_source('MsgClockDrawable.java', src, (ROOT/src).read_text(encoding='utf-8'), ['Original pending-message clock drawing and timing unchanged.'])

# Exact top/bottom glass stroke renderer, used by hardware and compatibility canvas paths.
src = JAVA+'ui/Components/blur3/drawable/BlurredBackgroundDrawable.java'
original = (ROOT/src).read_text(encoding='utf-8')
t = '''package org.telegram.ui.Components;
import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.core.math.MathUtils;
public final class GlassStroke {
'''+method(original, '    public static void drawStroke(')+'\n}\n'
write_source('GlassStroke.java', src, t, ['Original drawStroke method extracted unchanged; host supplies palette, bounds, radii and stroke paints.'])

# Retain ChatAvatarContainer's native measure/layout arithmetic, replacing account-backed image/text wrappers.
src = JAVA+'ui/Components/ChatAvatarContainer.java'
original = (ROOT/src).read_text(encoding='utf-8')
measure = method(original, '    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)')
measure = measure[:measure.index('        if (communityItem != null)')]+'''        setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
        if (customAvatar != null) customAvatar.measure(MeasureSpec.makeMeasureSpec(avatarImageView.getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(avatarImageView.getMeasuredHeight(), MeasureSpec.EXACTLY));
    }'''
measure = measure.replace('        } else if (animatedSubtitleTextView != null) {\n            animatedSubtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(20), MeasureSpec.AT_MOST));\n', '\n')
layout = method(original, '    protected void onLayout(boolean changed, int left, int top, int right, int bottom)')
layout = layout[:layout.index('        SimpleTextView titleTextLargerCopyView')]+'''
        if (getSubtitleTextView().getVisibility() != GONE) {
            titleTextView.layout(l, viewTop + dp(1.66f), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getMeasuredHeight() + dp(1.66f));
        } else {
            titleTextView.layout(l, viewTop + dp(11), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getMeasuredHeight() + dp(11));
        }
        subtitleTextView.layout(l, subtitleTop, l + subtitleTextView.getMeasuredWidth(), subtitleTop + subtitleTextView.getMeasuredHeight());
        if (customAvatar != null) customAvatar.layout(avatarImageView.getLeft(), avatarImageView.getTop(), avatarImageView.getRight(), avatarImageView.getBottom());
    }'''
layout = layout.replace('ActionBar.getCurrentActionBarHeight()', 'getMeasuredHeight()').replace('(occupyStatusBar ? AndroidUtilities.statusBarHeight : 0)', '0')
t = '''package org.telegram.ui.Components;
import static org.telegram.messenger.AndroidUtilities.dp;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
public final class ChatAvatarLayout extends ViewGroup {
    private final int avatarSizeInDp = 42;
    private final boolean glassMode = true;
    private final int leftPadding = 1, rightAvatarPadding = 0;
    private final ImageView avatarImageView;
    private final TextView titleTextView, subtitleTextView;
    private View customAvatar;
    private final TypingDotsDrawable typingDots = new TypingDotsDrawable(true);
    private boolean typing;
    public ChatAvatarLayout(Context context) {
        super(context);
        avatarImageView = new ImageView(context);
        avatarImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarImageView.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setOval(0, 0, view.getWidth(), view.getHeight()); }
        });
        avatarImageView.setClipToOutline(true);
        addView(avatarImageView);
        titleTextView = text(context, 17.5f); titleTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        subtitleTextView = text(context, 13.5f);
        addView(titleTextView); addView(subtitleTextView);
        setClickable(true); setFocusable(true);
    }
    private static TextView text(Context context, float size) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(size));
        view.setSingleLine(true); view.setEllipsize(TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        return view;
    }
    public View getSubtitleTextView() { return subtitleTextView; }
    public void setTitle(CharSequence text) { if (!TextUtils.equals(text, titleTextView.getText())) { titleTextView.setText(text); setContentDescription(text); } }
    public void setSubtitle(CharSequence text) { if (!TextUtils.equals(text, subtitleTextView.getText())) subtitleTextView.setText(text); subtitleTextView.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE); }
    public void setTyping(boolean value) {
        if (typing == value) return;
        typing = value;
        typingDots.setBounds(0, 0, dp(18), dp(18));
        subtitleTextView.setCompoundDrawablePadding(value ? dp(4) : 0);
        subtitleTextView.setCompoundDrawables(value ? typingDots : null, null, null, null);
        if (value && isAttachedToWindow()) typingDots.start(); else typingDots.stop();
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (typing) typingDots.start(); }
    @Override protected void onDetachedFromWindow() { typingDots.stop(); super.onDetachedFromWindow(); }
    public void setAvatar(Drawable drawable) { avatarImageView.setImageDrawable(drawable); }
    public void setColors(int title, int subtitle) { titleTextView.setTextColor(title); subtitleTextView.setTextColor(subtitle); typingDots.setColor(subtitle); }
    public void setAvatarView(View view) {
        if (customAvatar == view) return;
        if (customAvatar != null) removeView(customAvatar);
        customAvatar = view;
        if (view != null) { if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view); addView(view); }
        requestLayout();
    }
'''+measure+'\n'+layout+'\n}\n'
write_source('ChatAvatarLayout.java', src, t, ['Extracted avatar/text measure and glass-mode layout arithmetic, 42dp minus 2px avatar, 17.5/13.5dp text, 49.66dp text inset, 23.66dp subtitle offset.', 'Account-backed BackupImageView and SimpleTextView replaced with native ImageView/TextView; profile/story/timer/width-fade logic excluded. Custom avatar view is a host adapter at the same measured bounds. Status bar inset belongs to host. Native TextView compound drawable hosts original TypingDotsDrawable with attach/detach lifecycle.'])

for folder in ['drawable-mdpi', 'drawable-hdpi', 'drawable-xhdpi', 'drawable-xxhdpi', 'drawable']:
    for name in ['input_smile.webp', 'input_bot2.webp', 'ic_ab_other.webp', 'msg_input_attach2.webp', 'msg_input_attach2.xml', 'msg_check_s.webp', 'msg_halfcheck_s.webp']:
        src = 'TMessagesProj/src/main/res/'+folder+'/'+name
        if (ROOT/src).exists():
            dst = MODULE/'src/main/res'/folder/('telegram_'+name)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT/src, dst)
            record(src, dst, ['Original Telegram resource bytes preserved; resource name prefixed telegram_.'])
src = 'TMessagesProj/src/main/res/raw/default_pattern.svg'
dst = MODULE/'src/main/res/raw/telegram_default_pattern.svg'
shutil.copyfile(ROOT/src, dst)
record(src, dst, ['Unmodified original SVG; AndroidSVG replaces Telegram SvgHelper/native image pipeline. Rendered by source width, not screenshot imagery.'])

theme_src = JAVA+'ui/ActionBar/ThemeColors.java'
defaults = {key: int(value,16) for key,value in re.findall(r'defaultColors\[key_(\w+)\] = (0x[0-9a-fA-F]+);', (ROOT/theme_src).read_text(encoding='utf-8'))}
theme_source = (ROOT/theme_src).read_text(encoding='utf-8')
constant_colors = {key: int(value,16) for key,value in re.findall(r'(\w+) = (0x[0-9a-fA-F]+);', theme_source)}
for key, constant in re.findall(r'defaultColors\[key_(\w+)\] = ([A-Z_]+);', theme_source):
    if constant in constant_colors: defaults[key] = constant_colors[constant]
def palette(name):
    values = dict(defaults)
    if name:
        overrides = {key:int(value)&0xffffffff for key,value in re.findall(r'^(\w+)=(-?\d+)$', (ROOT/('TMessagesProj/src/main/assets/'+name+'.attheme')).read_text(encoding='utf-8'), re.M)}
        values.update(overrides)
        # Theme.getColor: a theme missing a new glass-tab key uses its explicit
        # legacy fallback value before consulting ThemeColors' light defaults.
        fallbacks = dict(re.findall(r'^\s*fallbackKeys.put\(key_(\w+), (?:Theme\.)?key_(\w+)\);', (ROOT/(JAVA+'ui/ActionBar/Theme.java')).read_text(encoding='utf-8'), re.M))
        for key in ['glass_tabUnselected', 'glass_tabSelected', 'glass_tabSelectedText']:
            if key not in overrides and fallbacks[key] in overrides:
                values[key] = overrides[fallbacks[key]]
    return values
names = [('DAY',None),('NIGHT','night'),('DARK_BLUE','darkblue')]
fields = ['background','incoming','outgoing','text','secondaryText','accent','glassTint','composerTint','icon','incomingTime','outgoingTime','hint','menuBackground','menuText','menuIcon','tabDefault','tabSelected','tabSelectedText','outgoingText','accentText']
keys = ['chat_wallpaper','chat_inBubble','chat_outBubble','chat_messageTextIn','actionBarDefaultSubtitle','chat_messagePanelCursor','chat_topPanelBackground','chat_messagePanelBackground','glass_defaultIcon','chat_inTimeText','chat_outTimeText','chat_messagePanelHint','actionBarDefaultSubmenuBackground','actionBarDefaultSubmenuItem','actionBarDefaultSubmenuItemIcon','glass_tabUnselected','glass_tabSelected','glass_tabSelectedText']
color_lines=[]
for const,name in names:
    values=palette(name)
    values.setdefault('chat_wallpaper',0xffdbddbb) # first color of Theme.createDefaultWallpaper()
    resolved=[values[key] for key in keys]
    # BlurredBackgroundProviderImpl's liquid-glass alpha=.85 (platform blur uses .76 at runtime).
    for index in (6,7): resolved[index]=(resolved[index]&0xffffff)|(int(255*.85)<<24)
    resolved.extend([values.get('chat_messageTextOut', values['chat_messageTextIn']), 0xffffffff])
    color_lines.append('    public static final TelegramChatColors '+const+' = new TelegramChatColors('+str(bool(name)).lower()+', '+', '.join('0x%08x'%v for v in resolved)+');')
text='''/* SPDX-License-Identifier: GPL-2.0-or-later; generated from Telegram themes. See UPSTREAM.json. */
package com.monica.telegramchat;
public final class TelegramChatColors {
    public final boolean dark;
    public final int '''+', '.join(fields)+''';
'''+ '\n'.join(color_lines)+'''
    public TelegramChatColors(boolean dark, '''+', '.join('int '+f for f in fields)+''') {
        this.dark = dark;
'''+ '\n'.join('        this.'+f+' = '+f+';' for f in fields)+'''
    }
}
'''
dst=MODULE/'src/main/java/com/monica/telegramchat/TelegramChatColors.java'
dst.write_text(text,encoding='utf-8')
for src in [theme_src,JAVA+'ui/ActionBar/Theme.java','TMessagesProj/src/main/assets/night.attheme','TMessagesProj/src/main/assets/darkblue.attheme']:
    record(src,dst,['Relevant source colors preserved as immutable DAY/NIGHT/DARK_BLUE values; glass-tab keys resolve original fallbacks. Public constructor allows the Steam host to supply its current application theme instead of selecting a Telegram preset; outgoing and accent text roles preserve foreground contrast.'])

# Record source-derived host adapters separately from method-preserving upstream classes.
for local, sources, modifications in [
 ('TelegramChatHeaderView.java', ['ui/ActionBar/ActionBar.java'], ['ActionBar.setupGlass and dispatchDraw visible glass geometry retained: 46dp islands, 6dp drawable padding, 23dp radius, 56dp row. Account/navigation/search state replaced by explicit host callbacks.']),
 ('TelegramChatComposerView.java', ['ui/Components/ChatActivityEnterView.java','ui/Components/chat/ChatInputViewsContainer.java'], ['Native input setup and 44dp controls, 52dp text inset, 22dp bubble radius, 7dp horizontal inset, 9dp bottom offset retained. Steam menu occupies unavailable Telegram audio/bot services. Text/IME and span state uses native EditText.']),
 ('TelegramGlassView.java', ['ui/Components/blur3/drawable/BlurredBackgroundDrawableRenderNode.java','ui/Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java'], ['Source-view RenderNode capture adapter; original AGSL, stroke routine, shader thickness/intensity/index, chat top/bottom palette/stroke settings retained. Process-wide blur source factories/downscale caches replaced with target-sized capture plus RenderEffect.']),
 ('TelegramWallpaperDrawable.java', ['ui/ActionBar/Theme.java'], ['Original default_pattern SVG; ByWidth scale from createDefaultWallpaper; AndroidSVG replaces SvgHelper. Dark pattern tint is host-adapted for the solid Night wallpaper; dynamic four-color wallpaper shader excluded.']),
 ('TelegramMessageTextView.java', ['ui/Cells/ChatMessageCell.java','ui/ActionBar/Theme.java'], ['Source text/time paint sizes, original makeStaticLayout and MessageDrawable; last-line time collision adapted to standalone text-only cell, 31dp horizontal budget, native URLSpan touch handling. Telegram account/reply/media/reaction machinery excluded.'])
]:
    for source in sources:
        record(JAVA+source, MODULE/'src/main/java/com/monica/telegramchat'/local, modifications)
