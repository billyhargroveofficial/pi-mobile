package ru.billyhargrove.pimobile.ui;
import android.content.Context;import android.graphics.*;import androidx.appcompat.widget.AppCompatTextView;
public final class FadeTextView extends AppCompatTextView {
 private final Paint fade=new Paint();
 public FadeTextView(Context c){super(c);setSingleLine(true);setEllipsize(null);setHorizontallyScrolling(true);fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));}
 @Override protected void onDraw(Canvas c){int layer=c.saveLayer(0,0,getWidth(),getHeight(),null);super.onDraw(c);float w=40*getResources().getDisplayMetrics().density;fade.setShader(new LinearGradient(getWidth()-w,0,getWidth(),0,Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP));c.drawRect(Math.max(0,getWidth()-w),0,getWidth(),getHeight(),fade);c.restoreToCount(layer);}
}
