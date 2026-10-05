package ru.billyhargrove.pimobile.ui;

import android.content.Context;
import android.graphics.*;
import android.widget.SeekBar;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.view.ViewCompat;
import java.util.*;
import org.json.JSONArray;
import ru.billyhargrove.pimobile.R;

/** Discrete native SeekBar: pill track, step dots, large thumb; keeps range accessibility. */
public final class EffortSlider extends AppCompatSeekBar {
 public interface Change {void changed(String value,boolean committed);}
 private final List<String> levels=new ArrayList<>();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private Change change;private float position;private boolean tracking;private int gestureStart;
 private final androidx.dynamicanimation.animation.SpringAnimation travel=new androidx.dynamicanimation.animation.SpringAnimation(this,new androidx.dynamicanimation.animation.FloatPropertyCompat<EffortSlider>("effortPosition"){public float getValue(EffortSlider view){return view.position;}public void setValue(EffortSlider view,float value){view.position=value;view.invalidate();}}).setSpring(new androidx.dynamicanimation.animation.SpringForce(0).setDampingRatio(.85f).setStiffness(260));
 public EffortSlider(Context c){super(c);travel.setMinimumVisibleChange(.001f);setPadding(dp(25),0,dp(25),0);setMinimumHeight(dp(60));setSplitTrack(false);
  setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onProgressChanged(SeekBar s,int progress,boolean fromUser){ViewCompat.setStateDescription(EffortSlider.this,label(value()));float target=levels.size()>1?progress/(float)(levels.size()-1):0;if(ExpressiveMotion.enabled()&&isLaidOut())travel.animateToFinalPosition(target);else{travel.cancel();position=target;invalidate();}if(change!=null)change.changed(value(),false);if(fromUser)performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);}
   public void onStartTrackingTouch(SeekBar s){getParent().requestDisallowInterceptTouchEvent(true);}
   public void onStopTrackingTouch(SeekBar s){getParent().requestDisallowInterceptTouchEvent(false);if(change!=null)change.changed(value(),true);}
  });setContentDescription("Effort level");
 }
 public void configure(JSONArray values,String preferred,Change change){this.change=null;levels.clear();if(values!=null)for(int i=0;i<values.length();i++){String v=values.optString(i);if(!v.isEmpty()&&!levels.contains(v))levels.add(v);}setMax(Math.max(1,levels.size()-1));setProgress(Math.max(0,levels.indexOf(preferred)));setEnabled(levels.size()>1);this.change=change;travel.cancel();position=levels.size()>1?getProgress()/(float)(levels.size()-1):0;ViewCompat.setStateDescription(this,label(value()));invalidate();}
 public String value(){return levels.isEmpty()?"off":levels.get(Math.min(getProgress(),levels.size()-1));}
 public static String label(String value){switch(value){case "off":return "Off";case "minimal":return "Minimal";case "low":return "Low";case "medium":return "Medium";case "high":return "High";case "xhigh":return "Extra High";case "max":return "Max";default:return value;}}
 @Override public boolean onKeyUp(int key,android.view.KeyEvent event){boolean handled=super.onKeyUp(key,event);if(change!=null&&(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT||key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT))change.changed(value(),true);return handled;}
 @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){int before=getProgress();boolean handled=super.performAccessibilityAction(action,args);if(handled&&before!=getProgress()&&change!=null)change.changed(value(),true);return handled;}
 @Override public boolean onTouchEvent(android.view.MotionEvent event){
  if(!isEnabled())return false;
  switch(event.getActionMasked()){
   case android.view.MotionEvent.ACTION_DOWN:gestureStart=getProgress();tracking=true;getParent().requestDisallowInterceptTouchEvent(true);setPressed(true);touchProgress(event.getX());return true;
   case android.view.MotionEvent.ACTION_MOVE:if(!tracking)return false;touchProgress(event.getX());return true;
   case android.view.MotionEvent.ACTION_UP:if(!tracking)return false;touchProgress(event.getX());tracking=false;setPressed(false);getParent().requestDisallowInterceptTouchEvent(false);performClick();if(change!=null)change.changed(value(),true);return true;
   case android.view.MotionEvent.ACTION_CANCEL:if(tracking){tracking=false;setPressed(false);setProgress(gestureStart);getParent().requestDisallowInterceptTouchEvent(false);}return true;
   default:return tracking;
  }
 }
 @Override public boolean performClick(){super.performClick();return true;}
 private void touchProgress(float x){float width=Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight());float fraction=Math.max(0,Math.min(1,(x-getPaddingLeft())/width));if(getLayoutDirection()==LAYOUT_DIRECTION_RTL)fraction=1-fraction;int next=Math.round(fraction*Math.max(0,levels.size()-1));if(next!=getProgress()){setProgress(next);performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);}}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 @Override protected void onDetachedFromWindow(){travel.cancel();super.onDetachedFromWindow();}
 @Override protected synchronized void onDraw(Canvas canvas){
  float cy=getHeight()/2f,r=dp(25),start=getPaddingLeft(),end=getWidth()-getPaddingRight(),fraction=Math.max(0,Math.min(1,position));
  boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;float x=rtl?end-(end-start)*fraction:start+(end-start)*fraction;
  paint.setColor(getContext().getColor(R.color.surface_alt));canvas.drawRoundRect(0,cy-r,getWidth(),cy+r,r,r,paint);
  float inset=dp(6),fillRadius=r-inset;
  paint.setColor(getContext().getColor(R.color.accent));paint.setAlpha(isEnabled()?255:100);canvas.drawRoundRect(rtl?x-fillRadius:inset,cy-fillRadius,rtl?getWidth()-inset:x+fillRadius,cy+fillRadius,fillRadius,fillRadius,paint);
  for(int i=0;i<levels.size();i++){float dot=levels.size()>1?start+(end-start)*i/(levels.size()-1):start;if(rtl)dot=getWidth()-dot;if(Math.abs(dot-x)<dp(20))continue;boolean filled=rtl?dot>x:dot<x;paint.setColor(getContext().getColor(filled?R.color.on_accent:R.color.text_primary));paint.setAlpha(isEnabled()?170:90);canvas.drawCircle(dot,cy,dp(3),paint);}
  paint.setColor(Color.WHITE);paint.setAlpha(255);canvas.drawCircle(x,cy,dp(16),paint);
 }
}
