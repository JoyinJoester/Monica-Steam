/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.monica.telegramchat;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Steam bitmap/initials boundary replacing account-bound Telegram ImageReceiver. */
public final class SteamAvatarView extends View {
    private final Paint paint = new Paint(3);
    private final RectF rect = new RectF();
    private Bitmap bitmap;
    private String initials = "";
    private int color;
    private int textColor;
    private float radius;
    public SteamAvatarView(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    public void setRoundRadius(int radius) { this.radius=radius; }
    public void setBitmap(Bitmap bitmap) { this.bitmap=bitmap; invalidate(); }
    public void setInitials(String name, int color, int textColor) {
        String next = name.isEmpty() ? "" : name.substring(0, name.offsetByCodePoints(0, 1));
        initials=next; this.color=color; this.textColor=textColor; invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        rect.set(0,0,getWidth(),getHeight()); float r=radius > 0 ? radius : getWidth()/2f;
        paint.setColor(color); paint.setShader(null); canvas.drawRoundRect(rect,r,r,paint);
        if (bitmap != null && !bitmap.isRecycled()) {
            BitmapShader shader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            Matrix matrix=new Matrix(); float scale=Math.max(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());
            matrix.setScale(scale,scale); matrix.postTranslate((getWidth()-bitmap.getWidth()*scale)/2,(getHeight()-bitmap.getHeight()*scale)/2);
            shader.setLocalMatrix(matrix); paint.setShader(shader); canvas.drawRoundRect(rect,r,r,paint); paint.setShader(null);
        } else {
            paint.setColor(textColor); paint.setTextSize(getHeight()*.42f); paint.setTypeface(Typeface.create("sans-serif-medium",0));
            paint.setTextAlign(Paint.Align.CENTER); canvas.drawText(initials,getWidth()/2f,(getHeight()-paint.ascent()-paint.descent())/2,paint);
        }
    }
}
