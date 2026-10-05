package ru.billyhargrove.pimobile.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import org.json.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import ru.billyhargrove.pimobile.R;
import ru.billyhargrove.pimobile.net.*;

/** Read-only quotas from Orca. Missing values are never displayed as zero. */
public final class UsageCards {
 private final LinearLayout root;private final HttpApi api;private final Handler main=new Handler(Looper.getMainLooper());
 private String base="",token="";private boolean active,busy;private int generation;
 private final Runnable tick=()->refresh();
 public UsageCards(LinearLayout root,HttpApi api){this.root=root;this.api=api;root.setVisibility(View.GONE);}
 public void start(String base,String token){if(active&&this.base.equals(base)&&this.token.equals(token))return;stop();this.base=base;this.token=token;active=!base.isEmpty()&&!token.isEmpty();if(active)refresh();}
 public void stop(){active=false;generation++;busy=false;main.removeCallbacks(tick);root.removeAllViews();root.setVisibility(View.GONE);}
 public void refresh(){if(!active||busy)return;main.removeCallbacks(tick);busy=true;int gen=generation;String url=base,key=token;
  AppExecutors.io().execute(()->{JSONObject result;try{result=api.fetchUsage(url,key);}catch(Exception ignored){result=null;}JSONObject value=result;main.post(()->{if(gen!=generation||!active)return;busy=false;render(value);main.postDelayed(tick,60000);});});
 }
 private int dp(int n){return Math.round(n*root.getResources().getDisplayMetrics().density);}
 private TextView label(String value,int sp){TextView text=new TextView(root.getContext());text.setText(value);text.setTextSize(sp);text.setTextColor(root.getContext().getColor(R.color.text_secondary));return text;}
 private void render(JSONObject value){root.removeAllViews();root.setVisibility(View.VISIBLE);Context c=root.getContext();JSONArray providers=value==null?null:value.optJSONArray("providers");
  HorizontalScrollView scroll=new HorizontalScrollView(c);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);scroll.addView(row);root.addView(scroll);
  for(String provider:new String[]{"codex","cursor","grok"}){JSONObject data=null;if(providers!=null)for(int i=0;i<providers.length();i++){JSONObject p=providers.optJSONObject(i);if(p!=null&&provider.equals(p.optString("provider")))data=p;}
   LinearLayout card=new LinearLayout(c);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(BubbleColors.background(c,c.getColor(R.color.surface)));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(184),-2);params.setMarginEnd(dp(8));row.addView(card,params);
   String name=provider.substring(0,1).toUpperCase(Locale.ENGLISH)+provider.substring(1);TextView title=label(name,14);title.setTextColor(c.getColor(R.color.text_primary));card.addView(title);
   JSONArray windows=data==null?null:data.optJSONArray("windows");long updated=data==null?0:data.optLong("updatedAt");boolean stale=updated<=0||System.currentTimeMillis()-updated>15*60*1000||!"ok".equals(data.optString("status"));
   if(windows==null||windows.length()==0){card.addView(label("Usage unavailable",12));continue;}
   for(int i=0;i<Math.min(4,windows.length());i++){JSONObject w=windows.optJSONObject(i);if(w==null||!w.has("usedPercent"))continue;double used=w.optDouble("usedPercent",Double.NaN);if(!Double.isFinite(used)||used<0)continue;String window=w.optString("name","Usage");card.addView(label(window+" · "+String.format(Locale.ENGLISH,"%.0f%% used",used),12));LinearProgressIndicator bar=new LinearProgressIndicator(c);bar.setMax(100);bar.setProgress((int)Math.min(100,Math.round(used)));bar.setContentDescription(window+": "+Math.round(used)+" percent used");card.addView(bar,new LinearLayout.LayoutParams(-1,dp(6)));long reset=w.optLong("resetsAt");if(reset>0){if(reset<100000000000L)reset*=1000;card.addView(label("Resets "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,Locale.ENGLISH).format(new Date(reset)),10));}}
   card.addView(label(stale?"Cached · may be outdated":"From Orca",10));
  }
 }
}
