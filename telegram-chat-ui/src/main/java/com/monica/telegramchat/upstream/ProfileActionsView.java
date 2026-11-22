// Adapted from Telegram Android 13.0.3; GPL-2.0-or-later. See UPSTREAM.json.
package com.monica.telegramchat.upstream;
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
    public ProfileActionsView(Context context, int targetHeight) {
        super(context); com.monica.telegramchat.TelegramUi.initialize(context);
        currentHeight = targetHeight;

        paint.setColor(Color.BLACK);
        paint.setAlpha(40);

        xpadding = dpf2(14);
        ypadding = dpf2(12);
        top = dpf2(8);
        textPadding = dpf2(4);

        this.targetHeight = (int) (targetHeight - ypadding - top);

        setBackgroundColor(0);

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }    public void setOnActionClickListener(OnActionClickListener onActionClickListener) {
        this.onActionClickListener = onActionClickListener;
    }
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.makeMeasureSpec((int) (targetHeight + top + ypadding), MeasureSpec.EXACTLY)
        );
    }
    private float getItemWidth() {
        int w = getMeasuredWidth();
        float betweenPadding = xpadding / 2f;
        return (w - betweenPadding * (activeCount - 1) - xpadding * 2f) / activeCount;
    }
    private void updateBounds(Action action) {
        final float cx = action.rect.centerX();
        final float cy = action.rect.centerY();

        final int drawableSize = dp(24);
        final float drawableR = drawableSize * 0.5f;

        action.text.setMaxWidth(action.rect.width() - dp(2));
        action.textScale = action.text.getLineCount() >= 3 ? 0.75f : action.text.getLineCount() >= 2 ? 0.85f : 1.0f;
        final float drawableTop = Math.max(0, (targetHeight - action.text.getHeight() * action.textScale) / 3f + dpf2(1.33f));
        action.setBounds(
            (int) (cx - drawableR),
            (int) (drawableTop),
            (int) (cx + drawableR),
            (int) (drawableTop + drawableSize)
        );
    }
    private void drawActionDrawable(Canvas canvas, Drawable drawable, float alpha) {
        if (drawable == null) {
            return;
        }

        final int a = (int) (alpha * 255);

        drawable.setColorFilter(lastColorFilter);
        drawable.setAlpha(a);
        drawable.draw(canvas);
    }
    public float getRoundRadius() {
        return dp(16);
    }    protected void onDraw(@NonNull Canvas canvas) {
        if (clipHeight >= 0f) {
            final float bottom = clipHeight - getY();
            if (bottom <= 0) {
                return;
            }
            canvas.clipRect(0f, 0f, getMeasuredWidth(), bottom);
        }

        float height = Math.max(0f, currentHeight - ypadding - top);

        if (height <= 0f) {
            return;
        }

        final float betweenPadding = xpadding / 2f;
        final float width = getItemWidth();
        float left = xpadding;
        float r = getRoundRadius();



        Action newFirstAction = null, newLastAction = null;
        int c = actions.size();
        for (int i = 0; i < c; i++) {
            Action action = actions.get(i);
            if (action.isDeleted) continue;

            if (!action.isDeleting) {
                action.rect.set(left, top, left + width, top + height);
                left += width + betweenPadding;

                if (newFirstAction == null) {
                    newFirstAction = action;
                }
                newLastAction = action;
            }

            action.updatePosition();
    
        }
        firstAction = newFirstAction;
        lastAction = newLastAction;

        float fraction = clamp(height / targetHeight);
        float alphaFraction1 = clamp((fraction - 0.2f) / 0.8f);
        if (alphaFraction1 <= 0f) {
            return;
        }

        if (!ignoreRect) {
            for (int i = 0; i < c; i++) {
                Action action = actions.get(i);
                if (!action.isDeleted) {
                    scratch.set(action.rect);
                    scratch.inset(
                        action.rect.width() / 2.0f * (1.0f - action.getScale()),
                        action.rect.height() / 2.0f * (1.0f - action.getScale())
                    );
                    int wasAlpha = paint.getAlpha();
                    int newAlpha = (int) (action.getAlpha() * alphaFraction1 * wasAlpha);
                    paint.setAlpha((int) (newAlpha * (radialGradient != null ? 0.1f : 1f)));

                    paint.setShadowLayer(0, 0, 0, 0);

                    canvas.drawRoundRect(scratch, r, r, paint);
                    if (radialGradient != null) {
                        int wasAlpha2 = shaderPaint.getAlpha();
                        shaderPaint.setAlpha((int) (action.getAlpha() * alphaFraction1 * wasAlpha2));
                        matrix.setTranslate(scratch.left, scratch.top);
                        radialGradient.setLocalMatrix(matrix);
                        canvas.drawRoundRect(scratch, r, r, shaderPaint);
                        shaderPaint.setAlpha(wasAlpha2);
                    }
                    paint.setAlpha(wasAlpha);
                }
            }
        }



        float alphaFraction2 = clamp((fraction - 0.4f) / 0.6f);
        if (alphaFraction2 > 0f) {
            for (int i = 0; i < c; i++) {
                drawAction(canvas, actions.get(i), fraction, alphaFraction2);
            }
        }
    }    private void drawAction(Canvas canvas, Action action, float fraction, float alpha) {
        if (action == null || action.isDeleted) {
            return;
        }

        final boolean isButtonColorLight = isButtonColorLight();
        float useFilledWhiteIcon = !isButtonColorLight ? 1 : MathUtils.clamp((parentExpanded - 0.75f) / 0.25f, 0, 1);
        if (isButtonColorLight && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            useFilledWhiteIcon = 0f;
        }

        final int textColor = actionTextColor;
        if (lastColorFilter == null || lastColorFilterColor != textColor) {
            lastColorFilterColor = textColor;
            lastColorFilter = new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN);
        }

        canvas.save();
        alpha *= action.getAlpha();
        final float cx = action.rect.centerX();
        final float cy = action.rect.centerY();
        fraction *= action.getScale();
        canvas.scale(fraction, fraction, cx, cy);
        canvas.clipRect(action.rect);

        updateBounds(action);

        final float textY = action.bounds.bottom + action.bounds.top - action.text.getHeight() * action.textScale / 2.0f - dp(4.66f);
        canvas.save();
        canvas.scale(action.textScale, action.textScale, cx, textY + action.text.getHeight() * action.textScale / 2.0f);
        action.text.draw(canvas, cx - action.text.getWidth() / 2f, textY, textColor, alpha);
        canvas.restore();

        if (action.iconTranslationY != 0) {
            canvas.translate(0, action.iconTranslationY);
        }

        if (action.iconScale != 1f) {
            canvas.scale(action.iconScale, action.iconScale, action.bounds.centerX(), action.bounds.centerY());
        }
        if (!isAnimatingCallAction || action.key != KEY_CALL) {
            final float outlineAlpha = (1f - useFilledWhiteIcon) * alpha;
            final float filledAlpha = useFilledWhiteIcon * alpha;
{
                drawActionDrawable(canvas, action.drawableOutline, outlineAlpha);
                drawActionDrawable(canvas, action.drawableFilled, filledAlpha);
            }
        }

        canvas.restore();

    }    public boolean onTouchEvent(MotionEvent event) {
        if (currentHeight < dp(8)) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();
        int eventAction = event.getAction();

        if (eventAction == MotionEvent.ACTION_DOWN) {
            hit = null;
            int c = actions.size();
            for (int i = 0; i < c; i++) {
                Action a = actions.get(i);
                if (!a.isDeleting && a.rect.contains(x, y)) {
                    hit = a;
                    downX = x;
                    downY = y;
                    downTime = System.currentTimeMillis();
                    hit.bounce.setPressed(true);
//                    try {
//                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
//                    } catch (Exception ignore) {}
                    break;
                }
            }
        } else if (eventAction == MotionEvent.ACTION_MOVE) {
            if (hit != null) {
                if (Math.abs(x - downX) > 20 || Math.abs(y - downY) > 20) {
                    hit.bounce.setPressed(false);
                    hit = null;
                }
            }
        } else if (eventAction == MotionEvent.ACTION_UP || eventAction == MotionEvent.ACTION_CANCEL) {
            if (hit != null) {
                hit.bounce.setPressed(false);
                if (eventAction == MotionEvent.ACTION_UP && hit.rect.contains(x, y)) {
                    if (System.currentTimeMillis() - downTime > 250) {
                        try {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                        } catch (Exception ignore) {
                        }
                    }


                    hit.startTime = System.currentTimeMillis();
                    final Action finalHit = hit;
                    if (onActionClickListener != null) {
                        if (finalHit.callDelay == 0) {
                            onActionClickListener.onClick(hit.key, hit.rect.left, hit.rect.top);
                        } else {
                            postDelayed(() -> {
                                onActionClickListener.onClick(finalHit.key, finalHit.rect.left, finalHit.rect.top);
                            }, finalHit.callDelay);
                        }
                    }
                }
                hit = null;
                return true;
            }
        }
        return hit != null;
    }    private class Action {
        int key;

        private final ButtonBounce bounce = new ButtonBounce(ProfileActionsView.this);
        final RectF prevRect = new RectF();
        final RectF rect = new RectF();

        private final AnimatedFloat positionFraction = new AnimatedFloat(ProfileActionsView.this, 0, 250, CubicBezierInterpolator.DEFAULT);
        private final RectF to = new RectF();
        private final RectF from = new RectF();

        private final Rect bounds = new Rect();
        private Drawable drawableFilled;
        private Drawable drawableOutline;
        private Text text;
        private float textScale = 1.0f;

        public void setBounds(int l, int t, int r, int b) {
            bounds.set(l, t, r, b);
            checkBounds();
        }

        private void checkBounds() {

            if (drawableFilled != null) {
                drawableFilled.setBounds(bounds);
            }
            if (drawableOutline != null) {
                drawableOutline.setBounds(bounds);
            }
        }

        public void setText(CharSequence cs) {
            this.text = new Text(cs, 11, Typeface.create("sans-serif-medium", 0))
                .multiline(3)
                .align(Layout.Alignment.ALIGN_CENTER);
        }

        boolean isOpening = false;
        boolean isDeleting = false;
        boolean isDeleted = false;

        int iconTranslationY = 0;
        float iconScale = 1f;

        boolean isLoading;
        boolean supportsLoading;
        int supportsAnimate;
        int callDelay = 0;
        long startTime;
        int stopDelay;

        public Action() {
        }

        public Action(int key, CharSequence text, int filled, int outline) {
            this.key = key; setText(text);
            drawableFilled = getResources().getDrawable(filled, null).mutate();
            drawableOutline = getResources().getDrawable(outline, null).mutate();
        }

        public float getAlpha() {
            if (isDeleting) {
                return 1f - positionFraction.set(1f);
            } else if (isOpening) {
                return positionFraction.set(1f);
            } else {
                return 1f;
            }
        }







        public void updatePosition() {
            if (isDeleting) {
                animatePosition();
                return;
            }

            if (isOpeningLayout) {
                isOpening = false;
                prevRect.set(rect);
                from.set(rect);
                to.set(rect);
                positionFraction.set(1f, true);
                return;
            }

            if (to.isEmpty()) {
                isOpening = true;
                to.set(rect);
                from.set(rect);

                boolean fromRight = rect.left - 1 <= xpadding;
                boolean fromLeft = rect.right + 1 >= getMeasuredWidth() - xpadding;

                if ((fromRight && fromLeft) ||
                        (firstAction != null && firstAction.key == key) ||
                        (lastAction != null && lastAction.key == key)) {
                    fromRight = fromLeft = false;
                }

                if ((key == KEY_CALL || key == KEY_VIDEO) && mode == MODE_USER) {
                    fromLeft = false;
                    fromRight = true;
                } else if ((key == KEY_GIFT || key == KEY_DISCUSS) && mode == MODE_CHANNEL) {
                    fromLeft = false;
                    fromRight = true;
                } else if (fromRight && firstAction != null && !firstAction.isDeleting) {
                    fromLeft = true;
                    fromRight = false;
                } else if (fromLeft && lastAction != null && !lastAction.isDeleting) {
                    fromLeft = false;
                    fromRight = true;
                }

                if (fromRight) {
                    from.left = from.right;
                } else if (fromLeft) {
                    from.right = from.left;
                } else {
                    from.left = from.right = to.centerX();
                }

                positionFraction.set(0f, true);
            }

            if (!rect.equals(to)) {
                from.set(prevRect);
                to.set(rect);
                positionFraction.set(0f, true);
            }

            animatePosition();
            prevRect.set(rect);
        }

        private void animatePosition() {
            float fraction = positionFraction.set(1f);
            if (fraction != 1f) {
                rect.left = TelegramUi.lerp(from.left, to.left, fraction);
                rect.right = TelegramUi.lerp(from.right, to.right, fraction);
            } else {
                isOpening = false;
                if (isDeleting) {
                    isDeleted = true;
                }
            }
        }

        public float getScale() {
            return bounce.getScale(0.04f);
        }




    }    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        if (accessibilityNodeProvider == null) {
            accessibilityNodeProvider = new AccessibilityNodeProvider() {
                @Override
                public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
                    int[] pos = {0, 0};
                    getLocationOnScreen(pos);
                    if (virtualViewId == HOST_VIEW_ID) {
                        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(ProfileActionsView.this);
                        onInitializeAccessibilityNodeInfo(info);
                        info.setEnabled(true);

                        for (int i = 0; i < actions.size(); ++i) {
                            info.addChild(ProfileActionsView.this, actions.get(i).key);
                        }

                        return info;
                    } else {
                        Action action = null;
                        for (int i = 0; i < actions.size(); ++i) {
                            if (actions.get(i).key == virtualViewId) {
                                action = actions.get(i);
                                break;
                            }
                        }
                        if (action == null) return null;
                        if (action.rect.isEmpty()) return null;

                        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
                        info.setSource(ProfileActionsView.this, virtualViewId);
                        info.setParent(ProfileActionsView.this);
                        info.setPackageName(getContext().getPackageName());

                        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                        info.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                        info.setClickable(true);
                        info.setFocusable(true);
                        info.setEnabled(true);
                        info.setVisibleToUser(true);
                        info.setClassName(android.widget.Button.class.getName());

                        info.setText(action.text.getText());

                        Rect parentBounds = new Rect(
                                (int) action.rect.left,
                                (int) action.rect.top,
                                (int) action.rect.right,
                                (int) action.rect.bottom
                        );
                        info.setBoundsInParent(parentBounds);
                        parentBounds.offset(pos[0], pos[1]);
                        info.setBoundsInScreen(parentBounds);

                        return info;
                    }
                }

                @Override
                public boolean performAction(int virtualViewId, int action, @Nullable Bundle arguments) {
                    if (virtualViewId == HOST_VIEW_ID) {
                        return performAccessibilityAction(action, arguments);
                    }

                    Action button = null;
                    for (int i = 0; i < actions.size(); ++i) {
                        if (actions.get(i).key == virtualViewId) {
                            button = actions.get(i);
                            break;
                        }
                    }
                    if (button == null) return false;

                    if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                        sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                        return true;
                    } else if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                        if (onActionClickListener != null) {
                            onActionClickListener.onClick(virtualViewId, 0, 0);
                        }
                        return true;
                    }

                    return false;
                }

                private void sendAccessibilityEventForVirtualView(int viewId, int eventType) {
                    sendAccessibilityEventForVirtualView(viewId, eventType, null);
                }

                private void sendAccessibilityEventForVirtualView(int viewId, int eventType, String text) {
                    AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
                    if (am.isTouchExplorationEnabled()) {
                        AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
                        event.setPackageName(getContext().getPackageName());
                        event.setSource(ProfileActionsView.this, viewId);
                        if (text != null) {
                            event.getText().add(text);
                        }
                        if (getParent() != null) {
                            getParent().requestSendAccessibilityEvent(ProfileActionsView.this, event);
                        }
                    }
                }
            };
        }
        return accessibilityNodeProvider;
    }
    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
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
