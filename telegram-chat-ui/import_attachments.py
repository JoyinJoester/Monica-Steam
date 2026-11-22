"""Source attachment-tab geometry/assets and composer animation, via shared importer."""
src = JAVA+'ui/Components/ChatActivityEnterView.java'
original = (ROOT/src).read_text(encoding='utf-8')
animation = method(original, '    public ValueAnimator animateSendButton(boolean show)')
animation = animation.replace('public ValueAnimator animateSendButton(boolean show)', 'public static ValueAnimator animate(View view, boolean show)')
animation = re.sub(r'        if \(show && fromAlpha < 0.25f.*?\n        }\n', '', animation, flags=re.S)
animation = animation.replace('getSendButtonInternal()', 'view')
write_source('ComposerActionAnimator.java', src, '''package org.telegram.ui.Components;
import android.animation.ValueAnimator;
import android.view.View;
import static com.monica.telegramchat.TelegramUi.lerp;
public final class ComposerActionAnimator {
'''+animation+'\n}\n', ['Original animateSendButton interpolation and 0.1 scale retained; View supplied explicitly.', 'Custom Telegram SendButton.appear() branch excluded; native host combines both actions using original checkSendButton 220ms EASE_OUT_QUINT / 150ms timing.'])

src = JAVA+'ui/Components/glass/GlassTabView.java'
original = (ROOT/src).read_text(encoding='utf-8')
constructor = method(original, '    public GlassTabView(@NonNull Context context)')
constructor = constructor[:constructor.index('        counter =')]+'''        defaultTextPaint.setTextSize(dp(11));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        textView.setPadding(dp(8), 0, dp(8), 0);
        imageView.setLayoutParams(frame(24, 24, 4));
        selection.set(false, true);
        setClickable(true); setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        imageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }'''
constructor = constructor.replace('GlassTabView(', 'GlassAttachTab(').replace('@NonNull ', '')
constructor = constructor.replace('new RLottieImageView(context)', 'new LottieAnimationView(context)')
constructor = constructor.replace('LayoutHelper.createFrame(44, 44, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, -6, 0, 0)', 'frame(44, 44, -6)')
constructor = constructor.replace('LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 28.33f, 0, 0)', 'frame(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 28.33f)')
constructor = constructor.replace('AndroidUtilities.bold()', 'Typeface.create("sans-serif-medium", Typeface.NORMAL)')
width = method(original, '    public float measureAttachTabWidth()')
measure = method(original, '    public float measureTextWidth()')
text_set = method(original, '    public void setText(CharSequence text)')
t = '''package org.telegram.ui.Components;
import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import com.airbnb.lottie.*;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.monica.telegramchat.TelegramChatColors;
import static com.monica.telegramchat.TelegramUi.*;

/** Portable attachment-only subset of GlassTabView. See UPSTREAM.json. */
public final class GlassAttachTab extends FrameLayout {
    private final LottieAnimationView imageView;
    private final TextView textView;
    private final TextPaint defaultTextPaint;
    private final AnimatedFloat selection = new AnimatedFloat(this, 320, new DecelerateInterpolator());
    private boolean selected;
    private int forward, reverse;
    private int colorDefault, colorSelected, colorSelectedText;
    private int currentIconColor;
    private final LottieValueCallback<ColorFilter> colorFilter = new LottieValueCallback<ColorFilter>() {
        @Override public ColorFilter getValue(com.airbnb.lottie.value.LottieFrameInfo<ColorFilter> frame) {
            return new PorterDuffColorFilter(currentIconColor, PorterDuff.Mode.SRC_IN);
        }
    };
'''+constructor+'''
    private static LayoutParams frame(int width, int height, float top) {
        LayoutParams params = new LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        params.topMargin = dp(top); return params;
    }
    public void setAnimations(int forward, int reverse) {
        if (this.forward == forward && this.reverse == reverse) return;
        this.forward = forward; this.reverse = reverse;
        showAnimation(false);
    }
    private void showAnimation(boolean animated) {
        // Original checkPlayAnimation: choose filled/outline JSON by selection.
        imageView.cancelAnimation();
        imageView.setAnimation(selected ? forward : reverse);
        imageView.addValueCallback(new KeyPath("**"), LottieProperty.COLOR_FILTER, colorFilter);
        if (animated) { imageView.setProgress(0); imageView.playAnimation(); }
        else imageView.setProgress(.99f);
    }
    public void setTabSelected(boolean selected, boolean animated) {
        if (this.selected == selected) return;
        this.selected = selected;
        selection.set(selected, !animated);
        textView.setTypeface(Typeface.create(selected ? "sans-serif-black" : "sans-serif-medium", Typeface.NORMAL));
        showAnimation(animated); invalidate();
    }
    @Override public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (forward != 0) setTabSelected(pressed, true);
    }
    public void setColors(TelegramChatColors colors) {
        colorDefault = colors.tabDefault; colorSelected = colors.tabSelected;
        colorSelectedText = colors.tabSelectedText; invalidate();
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        final float factor = selection.getValue();
        // Original updateColors blending; explicit palette replaces Theme service.
        currentIconColor = ColorUtils.blendARGB(colorDefault, colorSelected, factor);
        textView.setTextColor(ColorUtils.blendARGB(colorDefault, colorSelectedText, factor));
        super.dispatchDraw(canvas);
    }
    @Override protected void onDetachedFromWindow() {
        imageView.cancelAnimation(); selection.set(selected, true);
        super.onDetachedFromWindow();
    }
'''+width+'\n'+measure+'\n'+text_set+'\n}\n'
write_source('GlassAttachTab.java', src, t, ['Original attachment 24dp icon/4dp top and 11dp medium label/28.33dp top geometry, text-width/padding measurement and theme blending retained.', 'Telegram RLottie renderer replaced by Airbnb Lottie using unmodified source JSON; account bots/counters/main-tabs interface excluded.', 'Original 320ms decelerating selection uses the already-extracted AnimatedFloat instead of vkryl BoolAnimator. Explicit host colors/lifecycle; parent reserves accessible 56dp touch area and adapts overflow width.'])
for name in ['tab_gallery','tab_gallery_reverse','tab_files','tab_files_reverse']:
    src = 'TMessagesProj/src/main/res/raw/'+name+'.json'
    dst = MODULE/'src/main/res/raw'/('telegram_'+name+'.json')
    shutil.copyfile(ROOT/src,dst)
    record(src,dst,['Unmodified original Telegram attachment Lottie JSON; rendered through portable Airbnb Lottie.'])
record(JAVA+'ui/Components/ChatAttachAlert.java', MODULE/'src/main/java/com/monica/telegramchat/TelegramAttachmentTabsView.java', ['Native host for source GlassTabView attachment-only subset, original tab assets and existing TelegramGlassView background.', 'Steam provides two supported actions: system gallery and system document picker. Does not claim full original ChatAttachAlert media grid, permission service or album layout.'])
