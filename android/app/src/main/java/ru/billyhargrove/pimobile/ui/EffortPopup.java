package ru.billyhargrove.pimobile.ui;

import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import ru.billyhargrove.pimobile.R;

/** Small anchored effort picker; leaves the composer/keyboard in place. */
public final class EffortPopup extends PopupWindow {
 public interface Apply {void apply(String level);}
 public EffortPopup(View anchor,JSONObject model,String current,Apply callback){this(anchor,model,current,true,()->{},callback);}
 public EffortPopup(View anchor,JSONObject model,String current,boolean editable,Runnable openModels,Apply callback){
  android.content.Context c=anchor.getContext();float density=c.getResources().getDisplayMetrics().density;int p=Math.round(16*density);
  LinearLayout root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(p,p,p,p);GradientDrawable bg=new GradientDrawable();bg.setColor(c.getColor(R.color.surface));bg.setCornerRadius(24*density);root.setBackground(bg);
  com.google.android.material.button.MaterialButton modelButton=new com.google.android.material.button.MaterialButton(c);modelButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));modelButton.setId(R.id.popupModelButton);modelButton.setText(model.optString("name",model.optString("id"))+" ▾");modelButton.setMaxLines(1);modelButton.setEllipsize(android.text.TextUtils.TruncateAt.END);ExpressiveMotion.press(modelButton);modelButton.setTextSize(15);modelButton.setTextColor(c.getColor(R.color.text_primary));modelButton.setOnClickListener(v->{dismiss();openModels.run();});root.addView(modelButton,new LinearLayout.LayoutParams(-1,Math.round(48*density)));
  TextView title=new TextView(c);title.setId(R.id.effortTitle);title.setText(EffortSlider.label(current)+" effort");title.setTextColor(c.getColor(R.color.text_primary));title.setTextSize(18);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,-2));
  EffortSlider slider=new EffortSlider(c);slider.setId(R.id.quickEffortSlider);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,Math.round(64*density));sp.topMargin=Math.round(8*density);root.addView(slider,sp);
  final boolean[] committedOnce={false};
  slider.setOnTouchListener((v,event)->committedOnce[0]);
  slider.configure(model.optJSONArray("thinkingLevels"),current,(value,committed)->{title.setText(EffortSlider.label(value)+" effort");if(committed&&editable&&!committedOnce[0]){committedOnce[0]=true;if(!value.equals(current))callback.apply(value);root.postDelayed(()->{if(isShowing())dismiss();},ExpressiveMotion.enabled()?450:0);}});
  slider.setEnabled(editable&&model.optJSONArray("thinkingLevels")!=null&&model.optJSONArray("thinkingLevels").length()>1);
  setContentView(root);Rect frame=new Rect();anchor.getWindowVisibleDisplayFrame(frame);setWidth(frame.width()-2*p);setHeight(-2);setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));setElevation(12*density);setFocusable(false);setOutsideTouchable(true);setInputMethodMode(INPUT_METHOD_NEEDED);setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
  root.measure(View.MeasureSpec.makeMeasureSpec(getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int[] xy=new int[2];anchor.getLocationOnScreen(xy);showAtLocation(anchor,Gravity.TOP|Gravity.LEFT,frame.left+p,Math.max(frame.top,xy[1]-root.getMeasuredHeight()-p));root.setPivotX(root.getMeasuredWidth()*.75f);root.setPivotY(root.getMeasuredHeight());root.post(()->ExpressiveMotion.reveal(root));
  androidx.activity.OnBackPressedCallback back=new androidx.activity.OnBackPressedCallback(true){public void handleOnBackPressed(){dismiss();}};
  if(c instanceof androidx.activity.OnBackPressedDispatcherOwner)((androidx.activity.OnBackPressedDispatcherOwner)c).getOnBackPressedDispatcher().addCallback(back);
  android.view.ViewTreeObserver.OnGlobalLayoutListener follow=()->{if(!isShowing())return;Rect f=new Rect();anchor.getWindowVisibleDisplayFrame(f);int[] pos=new int[2];anchor.getLocationOnScreen(pos);update(f.left+p,Math.max(f.top,pos[1]-root.getMeasuredHeight()-p),-1,-1);};
  anchor.getViewTreeObserver().addOnGlobalLayoutListener(follow);setOnDismissListener(()->{back.remove();if(anchor.getViewTreeObserver().isAlive())anchor.getViewTreeObserver().removeOnGlobalLayoutListener(follow);});
 }
}
