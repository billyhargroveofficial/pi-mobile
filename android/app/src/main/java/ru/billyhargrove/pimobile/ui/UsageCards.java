package ru.billyhargrove.pimobile.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import org.json.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import ru.billyhargrove.pimobile.R;
import ru.billyhargrove.pimobile.net.*;

/** Three quiet summary columns. All real windows remain available in a focused detail sheet. */
public final class UsageCards {
 private final LinearLayout root;private final HttpApi api;private final Handler main=new Handler(Looper.getMainLooper());
 private String base="",token="";private boolean active,busy;private int generation;private BottomSheetDialog details;
 private final Runnable tick=this::refresh;
 public UsageCards(LinearLayout root,HttpApi api){this.root=root;this.api=api;root.setVisibility(View.GONE);}
 public void start(String base,String token){if(active&&this.base.equals(base)&&this.token.equals(token))return;stop();this.base=base;this.token=token;active=!base.isEmpty()&&!token.isEmpty();if(active){render(null);refresh();}}
 public void stop(){active=false;generation++;busy=false;main.removeCallbacks(tick);if(details!=null){details.dismiss();details=null;}root.removeAllViews();root.setVisibility(View.GONE);}
 public void refresh(){if(!active||busy)return;main.removeCallbacks(tick);busy=true;int gen=generation;String url=base,key=token;
  AppExecutors.io().execute(()->{JSONObject value;try{value=api.fetchUsage(url,key);}catch(Exception ignored){value=null;}JSONObject result=value;main.post(()->{if(gen!=generation||!active)return;busy=false;render(result);main.postDelayed(tick,60000);});});
 }
 private int dp(int n){return Math.round(n*root.getResources().getDisplayMetrics().density);}
 private TextView label(Context c,String value,int size,int color){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(c.getColor(color));v.setIncludeFontPadding(false);return v;}
 private LinearLayout vertical(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
 private LinearLayout.LayoutParams spaced(int width,int height,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,height);p.topMargin=dp(top);return p;}
 private static String name(String provider){return provider.substring(0,1).toUpperCase(Locale.ENGLISH)+provider.substring(1);}
 private static JSONObject primary(JSONObject provider){JSONArray windows=provider==null?null:provider.optJSONArray("windows");JSONObject mostUsed=null;double maximum=-1;if(windows!=null)for(int i=0;i<windows.length();i++){JSONObject w=windows.optJSONObject(i);double used=w==null?Double.NaN:w.optDouble("usedPercent",Double.NaN);if(Double.isFinite(used)&&used>=0&&used>maximum){maximum=used;mostUsed=w;}}return mostUsed;}
 private static String windowName(JSONObject window){String name=window.optString("name","Usage");if("session".equals(name)){long minutes=window.optLong("windowMinutes");return minutes>0&&minutes%60==0?(minutes/60)+"-hour":"Session";}if("weekly".equals(name))return "Weekly";if("monthly".equals(name))return "Monthly";return name;}
 private static long timestamp(long value){return value>0&&value<100000000000L?value*1000:value;}
 private String resetLabel(JSONObject w){long reset=timestamp(w.optLong("resetsAt"));if(reset<=0)return "Reset not reported";long minutes=Math.max(0,(reset-System.currentTimeMillis()+59999)/60000);if(minutes==0)return "Reset due";if(minutes>=1440)return "Resets in "+((minutes+1439)/1440)+"d";if(minutes>=60)return "Resets in "+((minutes+59)/60)+"h";return "Resets in "+minutes+"m";}
 private int usageColor(double used){return root.getContext().getColor(used>=95?R.color.danger:used>=75?R.color.warning:R.color.text_primary);}
 private LinearProgressIndicator meter(Context c,double used){LinearProgressIndicator bar=new LinearProgressIndicator(c);bar.setTrackThickness(dp(4));bar.setTrackCornerRadius(dp(2));bar.setIndicatorTrackGapSize(0);bar.setTrackStopIndicatorSize(0);bar.setIndicatorColor(usageColor(used));bar.setTrackColor(c.getColor(R.color.outline_soft));bar.setMax(100);bar.setProgress((int)Math.max(0,Math.min(100,Math.round(used))));bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return bar;}
 /** Public only for deterministic preview/tests: data uses exactly the authenticated API schema. */
 public void render(JSONObject value){
  root.removeAllViews();root.setVisibility(View.VISIBLE);Context c=root.getContext();JSONArray providers=value==null?null:value.optJSONArray("providers");
  LinearLayout heading=new LinearLayout(c);heading.setGravity(Gravity.CENTER_VERTICAL);TextView title=label(c,"Usage",13,R.color.text_secondary);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView hint=label(c,"Tap for details",11,R.color.text_hint);heading.addView(hint);root.addView(heading,spaced(-1,-2,0));
  LinearLayout panel=new LinearLayout(c);panel.setOrientation(LinearLayout.HORIZONTAL);panel.setBaselineAligned(false);panel.setBackground(BubbleColors.background(c,c.getColor(R.color.surface)));panel.setPadding(dp(2),dp(4),dp(2),dp(4));
  boolean largeText=c.getResources().getConfiguration().fontScale>1.25f;
  if(largeText){HorizontalScrollView scroll=new HorizontalScrollView(c);scroll.setHorizontalScrollBarEnabled(false);scroll.addView(panel,new HorizontalScrollView.LayoutParams(-2,-2));root.addView(scroll,spaced(-1,-2,10));}else root.addView(panel,spaced(-1,-2,10));
  for(String provider:new String[]{"codex","cursor","grok"}){
   JSONObject data=null;if(providers!=null)for(int i=0;i<providers.length();i++){JSONObject p=providers.optJSONObject(i);if(p!=null&&provider.equals(p.optString("provider")))data=p;}
   JSONObject window=primary(data);JSONObject selected=data;boolean known=window!=null;double used=known?window.optDouble("usedPercent"):0;
   LinearLayout card=vertical(c);card.setId(provider.equals("codex")?R.id.usageCodex:provider.equals("cursor")?R.id.usageCursor:R.id.usageGrok);card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setMinimumHeight(dp(144));
   GradientDrawable mask=BubbleColors.background(c,c.getColor(R.color.surface));card.setBackground(new RippleDrawable(ColorStateList.valueOf(c.getColor(R.color.accent_soft)),mask,null));card.setFocusable(true);card.setClickable(true);card.setOnClickListener(v->showDetails(provider,selected));
   LinearLayout.LayoutParams cp=largeText?new LinearLayout.LayoutParams(dp((int)(156*c.getResources().getConfiguration().fontScale/1.35f)),-1):new LinearLayout.LayoutParams(0,-1,1);panel.addView(card,cp);
   TextView providerTitle=label(c,name(provider),13,R.color.text_primary);providerTitle.setTypeface(null,Typeface.BOLD);card.addView(providerTitle);
   TextView amount=label(c,known?Math.round(used)+"%":"—",28,known&&used>=95?R.color.danger:R.color.text_primary);amount.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));amount.setLetterSpacing(-.035f);card.addView(amount,spaced(-1,-2,16));
   TextView subtitle=label(c,known?windowName(window)+" used":"Unavailable",11,R.color.text_secondary);subtitle.setMaxLines(2);card.addView(subtitle,spaced(-1,-2,5));
   View track=known?meter(c,used):new View(c);if(!known)track.setBackground(BubbleColors.background(c,c.getColor(R.color.outline_soft)));card.addView(track,spaced(-1,dp(4),12));
   TextView reset=label(c,known?resetLabel(window):"Check in Orca",10,R.color.text_hint);reset.setMaxLines(2);card.addView(reset,spaced(-1,-2,8));
   long updated=data==null?0:timestamp(data.optLong("updatedAt"));boolean stale=known&&(updated<=0||System.currentTimeMillis()-updated>15*60*1000||!"ok".equals(data.optString("status")));
   if(stale)reset.setText("Cached · tap to check");
   card.setContentDescription(name(provider)+(known?", "+Math.round(used)+" percent used, "+windowName(window)+", "+reset.getText():", usage unavailable")+". Show details");
  }
 }
 private void showDetails(String provider,JSONObject data){
  Context c=root.getContext();LinearLayout body=vertical(c);body.setPadding(dp(24),dp(16),dp(24),dp(24));View grip=new View(c);grip.setBackground(BubbleColors.background(c,c.getColor(R.color.outline_soft)));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(32),dp(4));gp.gravity=Gravity.CENTER_HORIZONTAL;gp.bottomMargin=dp(24);body.addView(grip,gp);
  TextView title=label(c,name(provider)+" usage",24,R.color.text_primary);title.setTypeface(Typeface.create("sans-serif-medium",0));body.addView(title);
  body.addView(label(c,"Reported by your connected Orca",13,R.color.text_secondary),spaced(-1,-2,8));JSONArray windows=data==null?null:data.optJSONArray("windows");
  if(windows==null||windows.length()==0)body.addView(label(c,"Orca has not provided usage limits for this account. Open Orca to check the account connection.",15,R.color.text_secondary),spaced(-1,-2,28));
  else for(int i=0;i<windows.length();i++){JSONObject window=windows.optJSONObject(i);if(window==null)continue;double used=window.optDouble("usedPercent",Double.NaN);if(!Double.isFinite(used))continue;LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(label(c,windowName(window),15,R.color.text_primary),new LinearLayout.LayoutParams(0,-2,1));row.addView(label(c,Math.round(used)+"% used",15,used>=95?R.color.danger:R.color.text_primary));body.addView(row,spaced(-1,-2,24));body.addView(meter(c,used),spaced(-1,dp(5),10));long reset=timestamp(window.optLong("resetsAt"));body.addView(label(c,reset>0?"Resets "+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.ENGLISH).format(new Date(reset)):"Reset time not reported",12,R.color.text_secondary),spaced(-1,-2,10));}
  long updated=data==null?0:timestamp(data.optLong("updatedAt"));if(updated>0)body.addView(label(c,"Updated "+DateFormat.getTimeInstance(DateFormat.SHORT,Locale.ENGLISH).format(new Date(updated)),12,R.color.text_hint),spaced(-1,-2,28));
  ScrollView scroll=new ScrollView(c);scroll.addView(body);details=new BottomSheetDialog(c);details.setContentView(scroll);details.getBehavior().setSkipCollapsed(true);details.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);details.show();
 }
}
