package ru.billyhargrove.pimobile.ui;
import android.view.*;import android.widget.*;import android.graphics.drawable.GradientDrawable;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import ru.billyhargrove.pimobile.R;

/** Material animated indicators live outside the transcript, so pagination never inserts a jumping row. */
public final class ChatLoading {
 private final LinearLayout initial;private final CircularProgressIndicator older;private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());private boolean received;
 public ChatLoading(FrameLayout parent){android.content.Context c=parent.getContext();initial=new LinearLayout(c);initial.setOrientation(LinearLayout.VERTICAL);initial.setGravity(Gravity.CENTER);initial.setId(R.id.chatLoading);CircularProgressIndicator spinner=spinner(c,48);initial.addView(spinner);TextView label=new TextView(c);label.setText("Loading conversation…");label.setTextSize(14);label.setTextColor(c.getColor(R.color.text_secondary));label.setPadding(0,dp(c,12),0,0);initial.addView(label);parent.addView(initial,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
  older=spinner(c,32);older.setId(R.id.historyLoading);int pad=dp(c,8);older.setPadding(pad,pad,pad,pad);GradientDrawable bg=new GradientDrawable();bg.setColor(c.getColor(R.color.surface));bg.setShape(GradientDrawable.OVAL);older.setBackground(bg);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(c,48),dp(c,48),Gravity.TOP|Gravity.CENTER_HORIZONTAL);lp.topMargin=dp(c,4);parent.addView(older,lp);older.setVisibility(View.GONE);
  handler.postDelayed(()->{if(!received){spinner.setVisibility(View.GONE);label.setText("Still waiting for Pi. Check your connection.");}},20000);
 }
 private static int dp(android.content.Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 private static CircularProgressIndicator spinner(android.content.Context c,int size){CircularProgressIndicator p=new CircularProgressIndicator(c);p.setIndeterminate(true);p.setIndicatorSize(dp(c,size));p.setTrackThickness(dp(c,3));p.setTrackCornerRadius(dp(c,2));p.setIndicatorColor(c.getColor(R.color.text_primary));return p;}
 public boolean isLoading(){return !received;}
 public void loaded(){received=true;handler.removeCallbacksAndMessages(null);initial.setVisibility(View.GONE);}
 public void history(boolean loading){older.setVisibility(loading?View.VISIBLE:View.GONE);}
 public void close(){handler.removeCallbacksAndMessages(null);}
}
