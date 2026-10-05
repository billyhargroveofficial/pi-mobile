package ru.billyhargrove.pimobile.ui;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import org.json.*;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import ru.billyhargrove.pimobile.PiApp;
import ru.billyhargrove.pimobile.R;
import ru.billyhargrove.pimobile.net.AppExecutors;

/** Date-grouped archive, bounded skeletons, debounced search and scroll pagination. */
public final class ArchiveSheet extends BottomSheetDialog {
 public interface Listener {void resume(String id,String title);}
 public interface Loader {JSONObject fetch(int offset,String query) throws Exception;}
 public interface Delete {void remove(String id,Runnable success,java.util.function.Consumer<String> failure);}
 private Delete delete;
 private final Loader loader; private final Listener listener;
 private final List<JSONObject> rows=new ArrayList<>();private final List<Entry> entries=new ArrayList<>();
 private final Rows adapter=new Rows();private final RecyclerView list;private final TextView status;private final MaterialButton retry;
 private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
 private int offset,generation;private boolean loading,closed,hasMore;private String query="";private Runnable pendingSearch;
 private static final class Entry {int type;String label;JSONObject row;boolean first,last;Entry(int type){this.type=type;}}
 public ArchiveSheet(Context context,PiApp app,Listener listener){this(context,(offset,query)->app.api().fetchArchive(app.settings().baseUrl(),app.settings().token(),offset,query),listener);}
 public ArchiveSheet(Context context,PiApp app,Listener listener,Delete delete){this(context,app,listener);this.delete=delete;}
 public ArchiveSheet(Context context,Loader loader,Listener listener,Delete delete){this(context,loader,listener);this.delete=delete;}
 public ArchiveSheet(Context context,Loader loader,Listener listener){
  super(context);this.loader=loader;this.listener=listener;
  LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(10),dp(16),dp(16));GradientDrawable sheetBackground=shape(R.color.bg,0);float corner=dp(28);sheetBackground.setCornerRadii(new float[]{corner,corner,corner,corner,0,0,0,0});root.setBackground(sheetBackground);root.setClipToOutline(true);
  View grip=new View(context);grip.setBackground(shape(R.color.outline,2));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(32),dp(4));gp.gravity=Gravity.CENTER_HORIZONTAL;gp.bottomMargin=dp(8);root.addView(grip,gp);
  LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);
  TextView title=text(24,R.color.text_primary);title.setText("История");title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));header.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));title.setGravity(Gravity.CENTER_VERTICAL);
  MaterialButton close=(MaterialButton)LayoutInflater.from(context).inflate(R.layout.icon_button,header,false);close.setIconResource(R.drawable.ic_close);close.setContentDescription("Закрыть историю");close.setOnClickListener(v->dismiss());ExpressiveMotion.press(close);header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);
  LinearLayout searchBox=new LinearLayout(context);searchBox.setGravity(Gravity.CENTER_VERTICAL);searchBox.setPadding(dp(16),0,dp(12),0);searchBox.setBackground(shape(R.color.surface,24));
  ImageView searchIcon=new ImageView(context);searchIcon.setImageResource(android.R.drawable.ic_menu_search);searchIcon.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.text_secondary)));searchBox.addView(searchIcon,new LinearLayout.LayoutParams(dp(20),dp(20)));
  EditText search=new EditText(context);search.setId(R.id.archiveSearch);search.setTextSize(15);search.setTextColor(context.getColor(R.color.text_primary));search.setHintTextColor(context.getColor(R.color.text_hint));search.setHint("Найти диалог");search.setSingleLine(true);search.setBackground(null);search.setPadding(dp(12),0,0,0);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);searchBox.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams sb=new LinearLayout.LayoutParams(-1,dp(48));sb.topMargin=dp(8);sb.bottomMargin=dp(8);root.addView(searchBox,sb);
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int start,int before,int count){if(pendingSearch!=null)handler.removeCallbacks(pendingSearch);String q=s.toString().trim();pendingSearch=()->search(q);handler.postDelayed(pendingSearch,250);}});
  search.setOnEditorActionListener((v,action,event)->{if(action!=EditorInfo.IME_ACTION_SEARCH)return false;if(pendingSearch!=null)handler.removeCallbacks(pendingSearch);search(search.getText().toString().trim());((android.view.inputmethod.InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);return true;});
  list=new RecyclerView(context);list.setId(R.id.archiveList);list.setLayoutManager(new LinearLayoutManager(context));list.setAdapter(adapter);list.setItemAnimator(null);list.setClipToPadding(false);list.setPadding(0,0,0,dp(12));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  SwipeAction.attach(list,"Удалить",p->delete!=null&&p<entries.size()&&entries.get(p).type==1,p->{if(p<entries.size())confirmDelete(entries.get(p).row);});
  list.addOnScrollListener(new RecyclerView.OnScrollListener(){@Override public void onScrolled(RecyclerView v,int dx,int dy){if(dy>0&&hasMore&&((LinearLayoutManager)v.getLayoutManager()).findLastVisibleItemPosition()>=entries.size()-5)load();}});
  status=text(14,R.color.text_secondary);status.setGravity(Gravity.CENTER);status.setPadding(dp(12),dp(16),dp(12),dp(16));status.setVisibility(View.GONE);root.addView(status,new LinearLayout.LayoutParams(-1,-2));
  retry=new MaterialButton(context);retry.setText("Повторить");retry.setVisibility(View.GONE);retry.setOnClickListener(v->load());ExpressiveMotion.press(retry);root.addView(retry,new LinearLayout.LayoutParams(-1,dp(48)));
  setContentView(root);ViewGroup.LayoutParams sheetParams=root.getLayoutParams();sheetParams.height=(int)(context.getResources().getDisplayMetrics().heightPixels*.88f);root.setLayoutParams(sheetParams);
  // Set the initial state BEFORE the first layout, not through an animated onShow transition.
  getBehavior().setSkipCollapsed(true);getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
  androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{int bottom=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;v.setPadding(dp(16),dp(10),dp(16),dp(12)+bottom);return insets;});
  setOnShowListener(d->androidx.core.view.ViewCompat.requestApplyInsets(root));
  setOnDismissListener(d->{closed=true;generation++;handler.removeCallbacksAndMessages(null);});load();
 }
 private int dp(int n){return Math.round(n*getContext().getResources().getDisplayMetrics().density);}
 private TextView text(int sp,int color){TextView t=new TextView(getContext());t.setTextSize(sp);t.setTextColor(getContext().getColor(color));return t;}
 private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(getContext().getColor(color));d.setCornerRadius(dp(radius));return d;}
 private void confirmDelete(JSONObject row){
  if(row==null||delete==null)return;String id=row.optString("id");
  new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext()).setTitle("Удалить сессию с диска?").setMessage(row.optString("title")+"\n\nФайл истории Pi будет удалён безвозвратно. Это нельзя отменить.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->delete.remove(id,()->{if(closed)return;generation++;loading=false;rows.clear();offset=0;hasMore=false;load();},error->{if(closed)return;status.setText(error);status.setVisibility(View.VISIBLE);})).show();
 }
 private void search(String q){if(closed||q.equals(query))return;query=q;generation++;loading=false;hasMore=false;rows.clear();offset=0;load();list.scrollToPosition(0);}
 private String date(JSONObject row){try{return Instant.parse(row.optString("modified")).atZone(ZoneId.systemDefault()).toLocalDate().toString();}catch(Exception ignored){return "";}}
 private String dateLabel(String date){try{LocalDate d=LocalDate.parse(date),today=LocalDate.now();if(d.equals(today))return "Сегодня";if(d.equals(today.minusDays(1)))return "Вчера";return d.format(DateTimeFormatter.ofPattern(d.getYear()==today.getYear()?"d MMMM":"d MMMM yyyy",new Locale("ru")));}catch(Exception ignored){return "Ранее";}}
 private void rebuild(){
  entries.clear();String prior=null;Entry last=null;
  for(JSONObject row:rows){String day=date(row);boolean first=!day.equals(prior);if(first){if(last!=null)last.last=true;Entry section=new Entry(0);section.label=dateLabel(day);entries.add(section);prior=day;}Entry e=new Entry(1);e.row=row;e.first=first;entries.add(e);last=e;}
  if(last!=null)last.last=true;
  if(loading){boolean initial=rows.isEmpty();if(initial)entries.add(new Entry(3));int count=initial?6:2;for(int i=0;i<count;i++){Entry e=new Entry(2);e.first=initial&&i==0;e.last=i==count-1;entries.add(e);}}
  adapter.notifyDataSetChanged();
 }
 private void load(){
  if(loading||closed)return;loading=true;status.setVisibility(View.GONE);retry.setVisibility(View.GONE);rebuild();int version=generation,start=offset;String q=query;
  AppExecutors.io().execute(()->{JSONObject data=null;String error=null;try{data=loader.fetch(start,q);}catch(Exception e){error=e.getMessage();}JSONObject result=data;String failure=error;
   AppExecutors.main(()->{if(closed||version!=generation)return;loading=false;if(result==null){hasMore=false;rebuild();status.setText(failure==null?"Не удалось загрузить историю":failure);status.setVisibility(View.VISIBLE);retry.setVisibility(View.VISIBLE);return;}
    JSONArray items=result.optJSONArray("sessions");if(items!=null)for(int i=0;i<items.length();i++){JSONObject row=items.optJSONObject(i);if(row!=null)rows.add(row);}offset=result.optInt("nextOffset",start+40);hasMore=result.optBoolean("hasMore");rebuild();if(rows.isEmpty()){status.setText(query.isEmpty()?"Пока нет закрытых диалогов":"Ничего не найдено");status.setVisibility(View.VISIBLE);}
   });
  });
 }
 private final class Rows extends RecyclerView.Adapter<Holder>{
  @Override public int getItemViewType(int position){return entries.get(position).type;}
  @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
   if(type==0||type==3){SkeletonLabel section=new SkeletonLabel(getContext(),.22f);section.setTextSize(12);section.setTextColor(getContext().getColor(R.color.text_secondary));section.setTypeface(Typeface.create("sans-serif-medium",0));section.setGravity(Gravity.CENTER_VERTICAL);section.setPadding(dp(4),dp(8),0,0);section.setLayoutParams(new RecyclerView.LayoutParams(-1,dp(40)));return new Holder(section);}
   LinearLayout box=new LinearLayout(getContext());box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(dp(12),dp(12),dp(12),dp(12));RecyclerView.LayoutParams bp=new RecyclerView.LayoutParams(-1,dp(76));bp.bottomMargin=dp(2);box.setLayoutParams(bp);
   ImageView icon=new ImageView(getContext());icon.setImageResource(R.drawable.ic_chat);icon.setImageTintList(ColorStateList.valueOf(getContext().getColor(R.color.text_secondary)));icon.setPadding(dp(9),dp(9),dp(9),dp(9));icon.setBackground(shape(R.color.surface_alt,14));box.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));
   LinearLayout labels=new LinearLayout(getContext());labels.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(12);box.addView(labels,lp);Holder h=new Holder(box);h.icon=icon;h.title=new SkeletonLabel(getContext(),.78f);h.title.setTextSize(15);h.title.setTextColor(getContext().getColor(R.color.text_primary));h.title.setSingleLine();h.title.setEllipsize(TextUtils.TruncateAt.END);h.title.setTypeface(Typeface.create("sans-serif-medium",0));labels.addView(h.title,new LinearLayout.LayoutParams(-1,-2));h.detail=new SkeletonLabel(getContext(),.6f);h.detail.setTextSize(12);h.detail.setTextColor(getContext().getColor(R.color.text_secondary));h.detail.setSingleLine();h.detail.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams detail=new LinearLayout.LayoutParams(-1,-2);detail.topMargin=dp(4);labels.addView(h.detail,detail);TextView arrow=text(24,R.color.text_hint);h.arrow=arrow;arrow.setText("›");arrow.setGravity(Gravity.CENTER);box.addView(arrow,new LinearLayout.LayoutParams(dp(20),-1));return h;
  }
  @Override public void onBindViewHolder(Holder h,int position){Entry e=entries.get(position);if(e.type==0||e.type==3){SkeletonLabel section=(SkeletonLabel)h.itemView;section.loading=e.type==3;section.setText(section.loading?" ":e.label);return;}boolean loading=e.type==2;h.itemView.setId(loading?R.id.archiveSkeleton:View.NO_ID);h.title.loading=loading;h.detail.loading=loading;h.title.setText(loading?" ":e.row.optString("title"));h.detail.setText(loading?" ":e.row.optString("workspaceName")+" · "+e.row.optInt("messageCount")+" сообщ.");h.icon.setImageAlpha(loading?0:255);h.arrow.setVisibility(loading?View.INVISIBLE:View.VISIBLE);GradientDrawable bg=shape(R.color.surface,4);float top=dp(e.first?20:4),bottom=dp(e.last?20:4);bg.setCornerRadii(new float[]{top,top,top,top,bottom,bottom,bottom,bottom});h.itemView.setBackground(loading?bg:new RippleDrawable(ColorStateList.valueOf(getContext().getColor(R.color.accent_soft)),bg,null));h.itemView.setOnClickListener(loading?null:v->listener.resume(e.row.optString("id"),e.row.optString("title")));h.itemView.setImportantForAccessibility(loading?View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);}
  @Override public int getItemCount(){return entries.size();}
 }
 private static final class Holder extends RecyclerView.ViewHolder {SkeletonLabel title,detail;ImageView icon;TextView arrow;Holder(View v){super(v);}}
 /** Placeholders share the REAL TextView measurement, card, margins and date-header layout. */
 private static final class SkeletonLabel extends androidx.appcompat.widget.AppCompatTextView {
  boolean loading;private final float fraction;private final Paint paint=new Paint(3);
  SkeletonLabel(Context c,float fraction){super(c);this.fraction=fraction;}
  @Override protected void onDraw(Canvas c){if(!loading){super.onDraw(c);return;}paint.setColor(getContext().getColor(R.color.surface_alt));float height=getTextSize()*.62f,cy=getBaseline()+getPaint().getFontMetrics().ascent*.4f;float start=getPaddingLeft();c.drawRoundRect(start,cy-height/2,start+(getWidth()-getPaddingLeft()-getPaddingRight())*fraction,cy+height/2,height/3,height/3,paint);}
 }
}
