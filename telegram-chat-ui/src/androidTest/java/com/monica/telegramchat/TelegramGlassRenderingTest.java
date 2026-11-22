/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.widget.FrameLayout;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TestName;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SdkSuppress(minSdkVersion = 31)
public class TelegramGlassRenderingTest {
    @Rule public final TestName testName = new TestName();
    private int captureNumber;
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MenuPopupTestActivity activity;
    private FrameLayout root;
    private CountingSource source;
    private final ArrayList<TelegramGlassView> pills = new ArrayList<>();
    private void main(Runnable action) { instrumentation.runOnMainSync(action); }

    private final class CountingSource extends View {
        int draws, color = Color.RED;
        final Paint paint = new Paint();
        CountingSource() { super(activity); }
        @Override public void draw(Canvas canvas) { draws++; super.draw(canvas); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(color);
            paint.setColor(Color.BLUE);
            canvas.drawRect(getWidth() / 2f, 0, getWidth(), getHeight(), paint);
        }
    }

    private void launch() throws Exception {
        activity = (MenuPopupTestActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), MenuPopupTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(() -> {
            // API 35 edge-to-edge puts the host's legacy ActionBar over the first glass sample.
            // Production Compose hosts have no platform ActionBar; keep this pixel fixture bare too.
            if (activity.getActionBar() != null) activity.getActionBar().hide();
            root = new FrameLayout(activity);
            source = new CountingSource();
            root.addView(source, new FrameLayout.LayoutParams(-1, -1));
            for (int i = 0; i < 6; i++) {
                TelegramGlassView glass = new TelegramGlassView(activity);
                glass.setSourceView(source); glass.setTintColor(0); glass.setStrokeEnabled(false);
                glass.setCornerRadius(20); glass.setLiquidThickness(0);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(160, 80);
                lp.leftMargin = 20; lp.topMargin = 100 + i * 100;
                root.addView(glass, lp); pills.add(glass);
            }
            activity.setContentView(root);
        });
        frame(() -> {});
        Thread.sleep(150);
    }

    private void frame(Runnable change) throws Exception {
        CountDownLatch drawn = new CountDownLatch(1);
        main(() -> {
            root.getViewTreeObserver().registerFrameCommitCallback(drawn::countDown);
            change.run(); root.invalidate();
        });
        assertTrue("Hardware frame committed", drawn.await(5, TimeUnit.SECONDS));
        instrumentation.waitForIdleSync();
    }

    private Bitmap pixels() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(activity.getWindow().getDecorView().getWidth(),
                activity.getWindow().getDecorView().getHeight(), Bitmap.Config.ARGB_8888);
        CountDownLatch copied = new CountDownLatch(1);
        int[] result = {-1};
        PixelCopy.request(activity.getWindow(), bitmap, status -> { result[0] = status; copied.countDown(); }, new Handler(Looper.getMainLooper()));
        assertTrue(copied.await(5, TimeUnit.SECONDS)); assertEquals(PixelCopy.SUCCESS, result[0]);
        File folder = new File(activity.getFilesDir(), "glass-rendering-evidence");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(folder,
                testName.getMethodName() + "-" + (++captureNumber) + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        return bitmap;
    }

    private int center(Bitmap bitmap, View view) {
        int[] xy = new int[2]; main(() -> view.getLocationInWindow(xy));
        int pixel = bitmap.getPixel(xy[0] + view.getWidth() / 2, xy[1] + view.getHeight() / 2);
        System.out.println("GLASS_PIXEL=" + Integer.toHexString(pixel) + " at " + xy[0] + "," + xy[1]
                + " size=" + view.getWidth() + "x" + view.getHeight());
        return pixel;
    }

    @Test public void sixPillsShareOneCaptureAndRefreshLivePixels() throws Exception {
        launch();
        int[] before = new int[1]; main(() -> before[0] = source.draws);
        frame(() -> { source.color = Color.GREEN; source.invalidate(); pills.forEach(View::invalidate); });
        int[] delta = new int[1]; main(() -> delta[0] = source.draws - before[0]);
        System.out.println("GLASS_SOURCE_DRAWS_FOR_SIX_PILLS=" + delta[0]);
        // One normal traversal plus one shared capture. Formerly one capture per pill.
        assertTrue("Six glass pills must not redraw the source six times: " + delta[0], delta[0] <= 2);
        Bitmap bitmap = pixels();
        for (View glass : pills) assertTrue("Every pill updates to green", Color.green(center(bitmap, glass)) > 220);
        bitmap.recycle();
        main(() -> before[0] = source.draws);
        frame(() -> pills.forEach(View::invalidate));
        main(() -> assertEquals("Decorative redraw reuses unchanged source", before[0], source.draws));
    }

    @Test public void movingPillAndReplacingSourceKeepCorrectPixels() throws Exception {
        launch();
        TelegramGlassView pill = pills.get(0);
        frame(() -> pill.setTranslationX(root.getWidth() - 200));
        Bitmap moved = pixels();
        assertTrue("Moving the pill samples its new screen position", Color.blue(center(moved, pill)) > 220);
        moved.recycle();
        frame(() -> {
            CountingSource replacement = new CountingSource(); replacement.color = Color.GREEN;
            root.addView(replacement, 0, new FrameLayout.LayoutParams(-1, -1));
            pills.forEach(p -> p.setSourceView(replacement));
            root.removeView(source); source = replacement;
            pill.setTranslationX(0);
        });
        Bitmap replaced = pixels();
        assertTrue("No stale source after replacement", Color.green(center(replaced, pill)) > 220);
        replaced.recycle();
        frame(() -> pills.forEach(root::removeView));
        int[] before = new int[1]; main(() -> before[0] = source.draws);
        frame(() -> source.invalidate());
        main(() -> assertEquals("Detached glass leaves only the normal source draw", 1, source.draws - before[0]));
    }

    @Test @SdkSuppress(minSdkVersion = 33)
    public void kyantRefractionReusesEffectAndUpdatesResizedGeometry() throws Exception {
        launch();
        TelegramGlassView pill = pills.get(0);
        frame(() -> { pill.setLiquidThickness(12); pill.setCornerRadius(32); });
        Bitmap first = pixels();
        assertTrue("Runtime refraction renders the actual source", Color.red(center(first, pill)) > 220);
        first.recycle();
        frame(() -> {
            pill.getLayoutParams().width = 200; pill.requestLayout();
            pill.setCornerRadius(40); source.color = Color.GREEN; source.invalidate();
        });
        Bitmap resized = pixels();
        assertTrue("Cached shader receives current size and refreshed content", Color.green(center(resized, pill)) > 220);
        resized.recycle();
        frame(() -> pill.setLiquidStyle(false));
        frame(() -> pill.setLiquidStyle(true));
        Bitmap switched = pixels();
        assertTrue("Switching back restores refraction", Color.green(center(switched, pill)) > 220);
        switched.recycle();
    }

    @After public void close() {
        if (activity != null) main(activity::finish);
        instrumentation.waitForIdleSync();
    }
}
