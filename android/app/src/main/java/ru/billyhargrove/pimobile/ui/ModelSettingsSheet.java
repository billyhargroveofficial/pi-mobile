package ru.billyhargrove.pimobile.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.*;
import com.google.android.material.button.MaterialButton;
import org.json.*;
import java.util.*;
import ru.billyhargrove.pimobile.R;

/** Native model list and discrete effort control, populated only from this live Pi. */
public final class ModelSettingsSheet extends BottomSheetDialog {
 public interface Apply {void apply(String provider,String model,String effort);}
 private final MaterialButton apply;private final TextView status,effortTitle;private final EffortSlider effort;
 private final List<JSONObject> models=new ArrayList<>(),visible=new ArrayList<>();private final Rows adapter=new Rows();
 private JSONObject selected;private final boolean idle;private boolean pending,multipleProviders;private String preferred;
 public ModelSettingsSheet(Context context,JSONObject config,boolean idle,Apply callback){
  super(context);this.idle=idle;preferred=config.optString("thinkingLevel","off");
  LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(10),dp(20),dp(20));
  View handle=new View(context);GradientDrawable grip=new GradientDrawable();grip.setColor(context.getColor(R.color.text_hint));grip.setCornerRadius(dp(3));handle.setBackground(grip);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(32),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(14);root.addView(handle,hp);
  TextView title=text(18);title.setText("Configure");title.setTypeface(null,android.graphics.Typeface.BOLD);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,dp(30)));
  status=text(12);status.setTextColor(context.getColor(R.color.text_secondary));status.setGravity(Gravity.CENTER);status.setPadding(0,dp(4),0,dp(12));root.addView(status);
  status.setText(idle?"This session only":"Wait for the current task to finish");
  JSONArray array=config.optJSONArray("models");if(array!=null)for(int i=0;i<array.length();i++){JSONObject m=array.optJSONObject(i);if(m==null)continue;models.add(m);if(key(m).equals(config.optString("model")))selected=m;}
  Set<String> providers=new HashSet<>();for(JSONObject model:models)providers.add(model.optString("provider"));multipleProviders=providers.size()>1;
  visible.addAll(models);
  if(models.size()>12){EditText search=new EditText(context);search.setId(R.id.modelSearch);search.setHint("Search models");search.setSingleLine(true);search.setTextSize(14);root.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int start,int before,int count){String q=s.toString().toLowerCase(Locale.ROOT);visible.clear();for(JSONObject m:models)if((key(m)+" "+m.optString("name")).toLowerCase(Locale.ROOT).contains(q))visible.add(m);adapter.notifyDataSetChanged();}});}
  RecyclerView list=new RecyclerView(context);list.setId(R.id.modelSelector);list.setLayoutManager(new LinearLayoutManager(context));list.setAdapter(adapter);list.setItemAnimator(null);GradientDrawable group=new GradientDrawable();group.setColor(context.getColor(R.color.surface_alt));group.setCornerRadius(dp(22));list.setBackground(group);list.setClipToOutline(true);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  effortTitle=text(16);effortTitle.setGravity(Gravity.CENTER);effortTitle.setPadding(0,dp(18),0,dp(4));root.addView(effortTitle,new LinearLayout.LayoutParams(-1,-2));
  effort=new EffortSlider(context);effort.setId(R.id.effortSelector);root.addView(effort,new LinearLayout.LayoutParams(-1,dp(60)));
  apply=new MaterialButton(context);apply.setId(R.id.applyModelButton);apply.setText("Apply");apply.setTextSize(15);apply.setCornerRadius(dp(28));apply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(context.getColor(R.color.text_primary)));apply.setTextColor(context.getColor(R.color.bg));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(56));bp.topMargin=dp(12);root.addView(apply,bp);
  if(selected!=null)selectEffort(preferred);else{effortTitle.setText("Select a model");effort.setEnabled(false);}
  if(models.isEmpty())status.setText("Models unavailable. Run /reload in Pi when idle.");else if(config.optBoolean("modelsTruncated"))status.append(" · First 1,000 models");
  apply.setEnabled(idle&&selected!=null);
  apply.setOnClickListener(v->{if(!idle||selected==null||pending)return;pending=true;apply.setEnabled(false);effort.setEnabled(false);adapter.notifyDataSetChanged();status.setText("Waiting for Pi to confirm…");callback.apply(selected.optString("provider"),selected.optString("id"),effort.value());});
  setContentView(root);root.getLayoutParams().height=Math.min((int)(context.getResources().getDisplayMetrics().heightPixels*.85f),dp(360+Math.min(models.size(),7)*64+(models.size()>12?48:0)));
  androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{v.setPadding(dp(20),dp(10),dp(20),dp(20)+insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom);return insets;});
  setOnShowListener(d->{getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);getBehavior().setSkipCollapsed(true);list.post(()->ExpressiveMotion.reveal(list));if(selected!=null)list.scrollToPosition(models.indexOf(selected));});ExpressiveMotion.press(apply);
 }
 public void failed(String message){pending=false;status.setText(message);status.setTextColor(getContext().getColor(R.color.danger));apply.setEnabled(idle&&selected!=null);effort.setEnabled(idle&&selected!=null&&selected.optJSONArray("thinkingLevels")!=null&&selected.optJSONArray("thinkingLevels").length()>1);adapter.notifyDataSetChanged();}
 private void selectEffort(String wanted){effort.configure(selected.optJSONArray("thinkingLevels"),wanted,(value,committed)->{preferred=value;effortTitle.setText(EffortSlider.label(value)+" effort");});preferred=effort.value();effortTitle.setText(EffortSlider.label(preferred)+" effort");effort.setEnabled(idle&&!pending&&selected.optJSONArray("thinkingLevels")!=null&&selected.optJSONArray("thinkingLevels").length()>1);}
 private static String key(JSONObject m){return m.optString("provider")+"/"+m.optString("id");}
 private int dp(int n){return Math.round(n*getContext().getResources().getDisplayMetrics().density);}
 private TextView text(int sp){TextView t=new TextView(getContext());t.setTextSize(sp);t.setTextColor(getContext().getColor(R.color.text_primary));return t;}
 private final class Rows extends RecyclerView.Adapter<Row>{
  @Override public Row onCreateViewHolder(ViewGroup parent,int type){LinearLayout box=new LinearLayout(getContext());box.setOrientation(LinearLayout.HORIZONTAL);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(dp(16),dp(10),dp(16),dp(10));box.setMinimumHeight(dp(multipleProviders?64:52));RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.bottomMargin=dp(1);box.setLayoutParams(lp);return new Row(box);}
  @Override public void onBindViewHolder(Row h,int position){JSONObject m=visible.get(position);boolean chosen=selected!=null&&key(m).equals(key(selected));h.title.setText(m.optString("name",m.optString("id")));h.subtitle.setText(m.optString("provider"));h.subtitle.setVisibility(multipleProviders?View.VISIBLE:View.GONE);h.check.setText(chosen?"✓":"");
   GradientDrawable bg=new GradientDrawable();bg.setColor(getContext().getColor(R.color.surface_alt));float top=position==0?dp(22):0,bottom=position==visible.size()-1?dp(22):0;bg.setCornerRadii(new float[]{top,top,top,top,bottom,bottom,bottom,bottom});h.itemView.setBackground(bg);h.itemView.setEnabled(idle&&!pending);h.itemView.setAlpha(idle?1f:.6f);h.itemView.setContentDescription(h.title.getText()+", "+m.optString("provider")+(chosen?", selected":""));h.itemView.setSelected(chosen);
   h.itemView.setOnClickListener(v->{if(!idle||pending)return;selected=m;selectEffort(preferred);apply.setEnabled(true);adapter.notifyDataSetChanged();});
  }
  @Override public int getItemCount(){return visible.size();}
 }
 private final class Row extends RecyclerView.ViewHolder{final TextView title,subtitle,check;Row(LinearLayout box){super(box);LinearLayout labels=new LinearLayout(getContext());labels.setOrientation(LinearLayout.VERTICAL);title=text(16);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);subtitle=text(11);subtitle.setTextColor(getContext().getColor(R.color.text_secondary));labels.addView(title);labels.addView(subtitle);box.addView(labels,new LinearLayout.LayoutParams(0,-2,1));check=text(22);check.setGravity(Gravity.CENTER);box.addView(check,new LinearLayout.LayoutParams(dp(32),dp(32)));box.setFocusable(true);ExpressiveMotion.press(box);}}
}
