"""Portable native popup rows and real Telegram popup animation; executed by importer."""
src = JAVA+'messenger/AndroidUtilities.java'
original = (ROOT/src).read_text(encoding='utf-8')
t = '''package org.telegram.ui.Components;
import androidx.core.math.MathUtils;
public final class PopupCascade {
'''+method(original, '    public static float cascade(float fullAnimationT, float position, float count, float waveLength)')+'\n}\n'
write_source('PopupCascade.java', src, t, ['Original cascade timing method extracted unchanged.'])

src = JAVA+'ui/ActionBar/ActionBarPopupWindow.java'
original = (ROOT/src).read_text(encoding='utf-8')
animation = method(original, '    public static AnimatorSet startAnimation(ActionBarPopupWindowLayout content)')
animation = animation.replace('ActionBarPopupWindowLayout', 'TelegramPopupLayout')
animation = animation.replace('AndroidUtilities.cascade', 'PopupCascade.cascade')
animation = animation.replace('''        if (content.getSwipeBack() != null) {
            content.getSwipeBack().invalidateTransforms();
            finalScaleY = content.backScaleY;
        }
''', '')
animation = re.sub(r'\s*if \(child instanceof GapView\) \{\s*continue;\s*\}', '', animation)
t = '''package org.telegram.ui.Components;
import static com.monica.telegramchat.TelegramUi.dp;
import android.animation.*;
import android.view.View;
import com.monica.telegramchat.TelegramPopupLayout;
public final class PopupEnterAnimator {
'''+animation+'\n}\n'
write_source('PopupEnterAnimator.java', src, t, ['Original static startAnimation with 150+16ms per visible row, cascading alpha and -6dp entry retained.', 'Layout type routed to explicit portable TelegramPopupLayout. Unused gap/swipe-back branches excluded; Telegram notification/account services excluded.'])
record(src, MODULE/'src/main/java/com/monica/telegramchat/TelegramPopupLayout.java', ['Native host for extracted popup animation properties and background reveal. Original 150ms dismiss translation/fade retained; composition owns popup positioning and lifecycle.'])
record(JAVA+'ui/ActionBar/ActionBarMenuSubItem.java', MODULE/'src/main/java/com/monica/telegramchat/TelegramMenuRow.java', ['Native row constructor geometry retained: 48dp row, 18dp horizontal padding, 40dp icon area, 43dp label offset, 16sp single-line text.', 'RLottieImageView and account-backed emoji label replaced by native ImageView/TextView; no checkboxes, account or swipe-back service.'])
for folder in ['drawable-mdpi','drawable-hdpi','drawable-xhdpi','drawable-xxhdpi']:
    for name in ['msg_copy.webp','msg_reactions.webp','msg_report.webp','msg_delete.webp','popup_fixed_alert2.9.png']:
        src = 'TMessagesProj/src/main/res/'+folder+'/'+name
        if (ROOT/src).exists():
            dst = MODULE/'src/main/res'/folder/('telegram_'+name)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT/src,dst)
            record(src,dst,['Original menu resource bytes retained; resource name prefixed telegram_.'])
