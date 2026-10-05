package ru.billyhargrove.pimobile.ui;
import android.content.Context;import android.graphics.*;import android.widget.LinearLayout;import ru.billyhargrove.pimobile.R;

/** Shared contiguous work surface. Only visible, running rows schedule animation. */
public final class WorkSurface extends LinearLayout {
 private final Paint paint=new Paint(3);private final Path clip=new Path();private boolean top,bottom,active;private final float radius;
 public WorkSurface(Context c){super(c);setOrientation(VERTICAL);setWillNotDraw(false);radius=20*c.getResources().getDisplayMetrics().density;}
 public void shape(boolean top,boolean bottom,boolean active){this.top=top;this.bottom=bottom;this.active=active;invalidate();}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float t=top?radius:0,b=bottom?radius:0;clip.reset();clip.addRoundRect(new RectF(0,0,getWidth(),getHeight()),new float[]{t,t,t,t,b,b,b,b},Path.Direction.CW);canvas.save();canvas.clipPath(clip);paint.setShader(null);paint.setColor(getContext().getColor(R.color.bubble_tool));canvas.drawPaint(paint);
  if(active&&ExpressiveMotion.enabled()){float x=((android.os.SystemClock.uptimeMillis()%2400)/2400f*2-0.5f)*getWidth();int rgb=getContext().getColor(R.color.accent)&0x00ffffff;paint.setShader(new LinearGradient(x-getWidth()*.5f,0,x+getWidth()*.5f,getHeight(),new int[]{rgb,0x1c000000|rgb,rgb},null,Shader.TileMode.CLAMP));canvas.drawPaint(paint);paint.setShader(null);if(isAttachedToWindow()&&isShown())postInvalidateOnAnimation();}canvas.restore();
 }
}
