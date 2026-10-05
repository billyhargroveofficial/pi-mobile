package ru.billyhargrove.pimobile.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import ru.billyhargrove.pimobile.R;

/** Rolling microphone RMS. Silence stays flat; never invents an audio signal. */
public final class VoiceWaveform extends View {
    private final float[] levels = new float[48];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int next;
    public VoiceWaveform(Context context) {
        super(context);
        setContentDescription("Microphone waveform");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void sample(float rms) {
        levels[next] = Math.max(0, Math.min(1, rms));
        next = (next + 1) % levels.length;
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float step = getWidth() / (float) levels.length;
        float width = Math.min(3 * density, step * .5f);
        paint.setColor(getContext().getColor(R.color.text_primary));
        for (int i = 0; i < levels.length; i++) {
            float value = levels[(next + i) % levels.length];
            float height = Math.max(2 * density, value * getHeight() * .85f);
            float x = (i + .5f) * step;
            paint.setAlpha((int)(70 + 185f * i / (levels.length - 1)));
            canvas.drawRoundRect(x-width/2, (getHeight()-height)/2, x+width/2, (getHeight()+height)/2, width/2, width/2, paint);
        }
    }
}
