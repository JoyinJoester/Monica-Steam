"""Reproducible extraction of the reviewed Telegram UI subset; no downloads.

Run with the Telegram source root as argv[1]. GPL-2.0-or-later.
"""
from pathlib import Path
import sys, re, hashlib, json, shutil

ROOT = Path(sys.argv[1])
MODULE = Path(__file__).parent
JAVA = 'TMessagesProj/src/main/java/org/telegram/'
OUT = MODULE / 'src/main/java/com/monica/telegramchat/upstream'
OUT.mkdir(parents=True, exist_ok=True)
records = []

def method(text, signature):
    start = text.index(signature)
    opening = text.index('{', start)
    depth = 1
    i = opening + 1
    while depth:
        depth += (text[i] == '{') - (text[i] == '}')
        i += 1
    return text[start:i]

def replace_method(text, signature, replacement=''):
    return text.replace(method(text, signature), replacement)

def record(source, destination, modifications):
    records.append({'upstream': source, 'sha256': hashlib.sha256((ROOT/source).read_bytes()).hexdigest(),
                    'local': str(destination.relative_to(MODULE)).replace('\\', '/'),
                    'license': 'GPL-2.0-or-later', 'modifications': modifications})

def write_source(name, source, text, changes):
    text = re.sub(r'package org\.telegram[^;]+;', 'package com.monica.telegramchat.upstream;', text)
    text = re.sub(r'import org\.telegram\.messenger\.AndroidUtilities;', 'import com.monica.telegramchat.TelegramUi;', text)
    text = text.replace('org.telegram.messenger.AndroidUtilities.', 'com.monica.telegramchat.TelegramUi.')
    text = text.replace('AndroidUtilities.', 'TelegramUi.')
    text = re.sub(r'import org\.telegram\.ui\.Components\.(?:CubicBezierInterpolator|AnimatedFloat|ButtonBounce);\n', '', text)
    notice = '// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.\n'
    dst = OUT/name
    dst.write_text(notice+text, encoding='utf-8')
    record(source, dst, ['Package relocated to com.monica.telegramchat.upstream; platform utilities routed through explicit TelegramUi bridge.']+changes)

for name in ['MessageBackgroundDrawable', 'CubicBezierInterpolator', 'ButtonBounce', 'AnimatedFloat']:
    src = JAVA+'ui/Components/'+name+'.java'
    write_source(name+'.java', src, (ROOT/src).read_text(), ['Drawing, interpolation and animation implementation preserved.'])

src = JAVA+'ui/ActionBar/BackDrawable.java'
t = (ROOT/src).read_text().replace('import org.telegram.messenger.Utilities;\n', '')
write_source('BackDrawable.java', src, t, ['Only unused Utilities import removed; original animation and vector drawing preserved.'])

src = JAVA+'ui/ActionBar/MenuDrawable.java'
t = (ROOT/src).read_text().replace('import org.telegram.ui.Components.MediaActionDrawable;\n', '')
for key, value in {'actionBarDefaultIcon':'0xff555555', 'actionBarDefault':'0xffffffff',
                   'actionBarActionModeDefaultIcon':'iconColor', 'actionBarActionModeDefault':'backColor'}.items():
    t = t.replace('Theme.getColor(Theme.key_'+key+')', value)
t = t.replace('MediaActionDrawable.getCircleValue(downloadRadOffset)', '(downloadRadOffset % 360f)')
write_source('MenuDrawable.java', src, t, ['Theme globals replaced by explicit iconColor/backColor; download angle wrapping uses remainder. Original morph/path/time code retained.'])

src = JAVA+'ui/Components/blur3/utils/NinePatchBuilder.java'
t = (ROOT/src).read_text().replace('import org.telegram.messenger.ApplicationLoader;', 'import com.monica.telegramchat.TelegramUi;')
t = t.replace('ApplicationLoader.applicationContext.getResources()', 'TelegramUi.resources()')
write_source('NinePatchBuilder.java', src, t, ['Resources passed through TelegramUi; bitmap generation and serialized nine-patch chunk unchanged.'])

src = JAVA+'ui/ActionBar/MessageDrawable.java'
t = (ROOT/src).read_text()
t = t.replace('import org.telegram.messenger.SharedConfig;\n', '')
t = t.replace('import org.telegram.ui.Components.MotionBackgroundDrawable;\n', '')
t = t.replace('import org.telegram.ui.Components.blur3.utils.NinePatchBuilder;\n', '')
t = t.replace('Theme.ResourcesProvider', 'BubblePalette')
t = t.replace('Theme.', 'BubblePalette.')
t = t.replace('BubblePalette.getColor(', 'BubblePalette.DEFAULT.getColor(').replace('BubblePalette.getCurrentColor(', 'BubblePalette.DEFAULT.getCurrentColor(')
t = t.replace('SharedConfig.bubbleRadius', 'bubbleRadius')
t = t.replace('    public static MotionBackgroundDrawable[] motionBackground = new MotionBackgroundDrawable[3];', '    private float bubbleRadius = 17f;\n\n    public void setBubbleRadius(float radiusDp) { bubbleRadius = Math.max(0, radiusDp); invalidateSelf(); }')
t = replace_method(t, '    public void applyMatrixScale()', '    public void applyMatrixScale() {\n        // Moving wallpaper gradients are intentionally outside this standalone UI module.\n    }')
t = replace_method(t, '    public MotionBackgroundDrawable getMotionBackgroundDrawable()')
t = replace_method(t, '    public void setTop(int top, int backgroundWidth, int backgroundHeight, int heightOffset', '''    public void setTop(int top, int backgroundWidth, int backgroundHeight, int heightOffset, int blurredViewTopOffset, int blurredViewBottomOffset, boolean topNear, boolean bottomNear) {
        if (crossfadeFromDrawable != null) {
            crossfadeFromDrawable.setTop(top, backgroundWidth, backgroundHeight, heightOffset, blurredViewTopOffset, blurredViewBottomOffset, topNear, bottomNear);
        }
        int color = overrideColor != null ? overrideColor : getColor(isOut
                ? (isSelected ? BubblePalette.key_chat_outBubbleSelected : BubblePalette.key_chat_outBubble)
                : (isSelected ? BubblePalette.key_chat_inBubbleSelected : BubblePalette.key_chat_inBubble));
        paint.setColor(color);
        paint.setAlpha(alpha);
        currentBackgroundHeight = backgroundHeight;
        topY = top;
        isTopNear = topNear;
        isBottomNear = bottomNear;
    }''')
# Avoid swallowing errors during live extraction testing and preserve alpha with cached patches.
t = t.replace('if (background.getAlpha() != alpha)', 'if (background != null && background.getAlpha() != alpha)')
write_source('MessageDrawable.java', src, t, ['Original generatePath, makePath, draw/drawCached, grouping, shadow and nine-patch rendering retained.',
    'Telegram Theme and SharedConfig replaced with BubblePalette and per-instance radius.',
    'Removed MotionBackgroundDrawable animated wallpaper/theme preview shader setup from setTop; standalone bubbles use explicit solid color.',
    'Added null guard for optional failed nine-patch allocation in setAlpha.'])

src = JAVA+'ui/Components/TypingDotsDrawable.java'
t = (ROOT/src).read_text()
for imp in ['NotificationCenter', 'UserConfig']:
    t = t.replace('import org.telegram.messenger.'+imp+';\n', '')
t = t.replace('import org.telegram.ui.ActionBar.Theme;\n', '')
t = t.replace('    private int currentAccount = UserConfig.selectedAccount;\n', '')
t = t.replace('        if (createPaint) {\n            currentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);\n        }', '        currentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);')
t = t.replace('paint = Theme.chat_statusPaint;', 'paint = currentPaint;')
t = replace_method(t, '    private void checkUpdate()', '    private void checkUpdate() {\n        if (started) update();\n    }')
write_source('TypingDotsDrawable.java', src, t, ['Removed account selection and global notification animation locks; drawable owns its paint and follows start/stop lifecycle. Original dot pulse rendering/timing retained.'])
src = JAVA+'ui/Components/StatusDrawable.java'
t = (ROOT/src).read_text().replace('import org.telegram.messenger.utils.Choreographer60FpsContent;\n', '')
t = t.replace('Choreographer60FpsContent.getInstance().postInvalidateDrawable30fps(this);', 'scheduleSelf(this::invalidateSelf, android.os.SystemClock.uptimeMillis() + 33);')
write_source('StatusDrawable.java', src, t, ['30 fps invalidation uses Drawable.Callback scheduling instead of Telegram global Choreographer service.'])

src = JAVA+'ui/Components/blur3/LiquidGlassEffect.java'
t = (ROOT/src).read_text().replace('import org.telegram.messenger.R;', 'import com.monica.telegramchat.R;')
write_source('LiquidGlassEffect.java', src, t, ['Only raw-resource loader/package imports changed; AGSL uniforms, radius constraints and RenderEffect creation unchanged.'])

src = JAVA+'ui/Components/RecyclerItemsEnterAnimator.java'
t = (ROOT/src).read_text().replace('RecyclerListView', 'RecyclerView')
t = t.replace('        listView.setItemsEnterAnimator(this);\n', '')
t = replace_method(t, '    public View getProgressView()', '    public View getProgressView() {\n        return null; // Steam has no Telegram FlickerLoadingView row.\n    }')
write_source('RecyclerItemsEnterAnimator.java', src, t, ['RecyclerListView dependency changed to standard RecyclerView; host calls dispatchDraw/onDetached.',
    'Telegram FlickerLoadingView progress-row detection removed. Original staggered position-based alpha animation retained.'])

# Actual Telegram input cursor implementation, with platform IME/selection retained.
src = JAVA+'ui/Components/EditTextBoldCursor.java'
original = (ROOT/src).read_text()
selected = []
for signature in ['    public void addTextChangedListener(', '    public void removeTextChangedListener(',
        '    public void dispatchTextWatchersTextChanged(', '    public void setTextWatchersSuppressed(',
        '    public boolean isTextWatchersSuppressed(', '    public void setAllowDrawCursor(',
        '    public void setForceCursorEnd(', '    public void setCursorWidth(', '    public void setCursorColor(',
        '    public void setCursorSize(', '    private boolean updateCursorPosition()',
        '    private int clampHorizontalPosition(', '    private void updateCursorPosition(int',
        '    protected void drawCursor(']:
    selected.append(method(original, signature))
# Use upstream cursor construction, without hidden Editor API reflection or action-toolbar machinery.
cursor_init = original[original.index('            cursorDrawable = new ShapeDrawable()'):original.index('\n\n\n        try {', original.index('            cursorDrawable = new ShapeDrawable()'))]
cursor_init = cursor_init.rsplit('        }', 1)[0]
cursor_init = cursor_init.replace('return dp(cursorSize + 20);', 'return cursorSize + dp(20);')
draw_original = method(original, '    protected void onDraw(Canvas canvas)')
cursor_draw = draw_original[draw_original.index('            if (cursorDrawn && allowDrawCursor)'):draw_original.index('\n        if (lineVisible')]
# Last closing brace belongs to removed else branch.
cursor_draw = cursor_draw.rsplit('        }', 1)[0]
start = cursor_draw.index('                    if (getVerticalOffsetMethod != null)')
end = cursor_draw.index('                    canvas.translate(', start)
cursor_draw = cursor_draw[:start]+'''                    if ((getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
                        voffsetCursor = getTotalPaddingTop() - getExtendedPaddingTop();
                    }
'''+cursor_draw[end:]
cursor_draw = cursor_draw.replace('if (BuildVars.DEBUG_PRIVATE_VERSION) {\n                        throw new RuntimeException(exception);\n                    }', 'android.util.Log.w("TelegramCursor", "Cursor layout unavailable", exception);')
cursor_draw = '\n'.join(line[4:] if line.startswith('    ') else line for line in cursor_draw.splitlines())
t = '''/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * Copyright Nikolai Kudashov, 2013-2018.
 * Extracted cursor and text watcher implementation; see UPSTREAM.json.
 */
package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.List;
import com.monica.telegramchat.TelegramUi;

public class EditTextBoldCursor extends EditText {
    private GradientDrawable gradientDrawable;
    private ShapeDrawable cursorDrawable;
    private int cursorSize;
    private float cursorWidth = 2f;
    private float lineSpacingExtra;
    private boolean allowDrawCursor = true, forceCursorEnd, drawInMaim, cursorDrawn;
    private Rect rect = new Rect(), mTempRect;
    private CharSequence lastText;
    private int lastOffset;
    private List<TextWatcher> registeredTextWatchers = new ArrayList<>();
    private boolean isTextWatchersSuppressed;

    public EditTextBoldCursor(Context context) {
        super(context);
        TelegramUi.initialize(context);
        setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        cursorSize = dp(24);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
''' + cursor_init + '''
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        drawInMaim = true;
        super.onDraw(canvas);
        drawInMaim = false;
        // API26-28 retain the platform cursor; public cursor API begins at API29.
        if (cursorDrawable == null || getLayout() == null || getSelectionStart() < 0) return;
''' + cursor_draw + '''
    }

    @Override public void setLineSpacing(float add, float mult) {
        lineSpacingExtra = add;
        super.setLineSpacing(add, mult);
    }

    public void setHintText(CharSequence text) { setHint(text); }
    public void setHintColor(int color) { setHintTextColor(color); }

''' + '\n\n'.join(selected) + '\n}\n'
write_source('EditTextBoldCursor.java', src, t, ['Extracted actual cursor construction/drawing/position/clamping and watcher suppression methods.',
    'Base changed from Telegram EditTextEffects to Android EditText. Platform IME, accessibility, selection and hint rendering retained.',
    'Removed private Editor reflection, premium/custom floating toolbar, animated hints and global account/theme services.',
    'Public text cursor API29+; API26-28 safely retain platform cursor. Corrected dp-of-pixel intrinsic-height expression.'])

# Original packaged icons, not approximated vector redraws.
for folder in ['drawable-mdpi','drawable-hdpi','drawable-xhdpi','drawable-xxhdpi']:
    for name in ['msg_send.webp','input_attach.webp','msg_input_gift.webp']:
        src = 'TMessagesProj/src/main/res/'+folder+'/'+name
        if (ROOT/src).exists():
            dst = MODULE/'src/main/res'/folder/('telegram_'+name)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT/src, dst)
            record(src,dst,['Resource renamed with telegram_ prefix; binary content unchanged.'])
for item in (ROOT/'TMessagesProj/src/main/res/raw').glob('liquid_glass_shader.*'):
    dst = MODULE/'src/main/res/raw'/item.name
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(item,dst)
    record(str(item.relative_to(ROOT)).replace('\\','/'),dst,['Unmodified original AGSL source.'])
exec((MODULE/'import_chrome.py').read_text(encoding='utf-8'), globals())
exec((MODULE/'import_popups.py').read_text(encoding='utf-8'), globals())
exec((MODULE/'import_attachments.py').read_text(encoding='utf-8'), globals())
exec((MODULE/'import_pickers.py').read_text(encoding='utf-8'), globals())
exec((MODULE/'import_social.py').read_text(encoding='utf-8'), globals())
license_file = ROOT/'LICENSE'
shutil.copyfile(license_file, MODULE/'LICENSE.Telegram')
(MODULE/'UPSTREAM.json').write_text(json.dumps({'repository':'https://github.com/DrKLO/Telegram', 'version':'13.0.3',
    'source_snapshot':'local Telegram-master archive; no commit metadata available', 'files':records}, indent=2)+'\n')
print('Imported', len(records), 'upstream files')
