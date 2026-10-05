package ru.billyhargrove.pimobile.ui;

import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
import org.json.*;
import ru.billyhargrove.pimobile.R;
import ru.billyhargrove.pimobile.core.*;
import ru.billyhargrove.pimobile.net.MediaLoader;

/** Flat, virtualized rows form one contiguous work bubble per turn. */
public final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
 public interface LocalThumbProvider { Bitmap thumbnail(String requestId,int index); }
 public interface Listener {void onRetry(ChatMessage message);void onRestore(ChatMessage message);default void onDocument(String path){} }
 public static final String LOCAL_PREFIX="local:";
 public static ImageRef localRef(int index,String mime){return new ImageRef(LOCAL_PREFIX+index,mime);}
 private final MediaLoader loader;private final LocalThumbProvider thumbs;private final Listener listener;
 private final List<ChatMessage> messages=new ArrayList<>();private List<WorkTimeline.Row> rows=new ArrayList<>();
 private final Set<String> collapsed=new HashSet<>(),expandedTools=new HashSet<>(),workTurns=new HashSet<>();
 private final Map<String,JSONObject> turns=new HashMap<>();private String active="";
 private MarkdownRenderer markdown;private boolean closed;
 private final android.os.Handler timer=new android.os.Handler(android.os.Looper.getMainLooper());
 private final Runnable tick=new Runnable(){public void run(){if(closed)return;for(int i=0;i<rows.size();i++)if(rows.get(i).header&&rows.get(i).turn.equals(active))notifyItemChanged(i,"metrics");if(!active.isEmpty())timer.postDelayed(this,1000);}};
 public MessageAdapter(MediaLoader loader,LocalThumbProvider thumbs,Listener listener){this.loader=loader;this.thumbs=thumbs;this.listener=listener;}
 public void close(){closed=true;timer.removeCallbacks(tick);if(markdown!=null)markdown.close();}
 public void metadata(JSONObject frame){
  JSONArray list=frame.optJSONArray("turns");if(list!=null)for(int i=0;i<list.length();i++){JSONObject t=list.optJSONObject(i);if(t!=null)turns.put(t.optString("id"),t);}
  if(frame.has("activeTurnId"))active=frame.optString("activeTurnId","");
  timer.removeCallbacks(tick);timer.post(tick);
  for(int i=0;i<rows.size();i++)if(rows.get(i).work()||rows.get(i).message.role()==ChatMessage.Role.ASSISTANT)notifyItemChanged(i,"metrics");
 }
 public int size(){return rows.size();}
 public String keyAt(int pos){return pos>=0&&pos<rows.size()?rows.get(pos).key:"";}
 public int positionOf(String key){for(int i=0;i<rows.size();i++)if(rows.get(i).key.equals(key))return i;return -1;}
 public void submit(List<ChatMessage> source){messages.clear();String fallback="legacy";for(ChatMessage m:source){if(m.role()==ChatMessage.Role.USER)fallback=m.stableKey();messages.add("legacy".equals(m.turnId())?m.withPresentation(fallback,m.phase(),m.preview(),m.documentPath()):m);}rebuild();}
 private void rebuild(){
  List<WorkTimeline.Row> old=rows,next=WorkTimeline.build(messages,collapsed);
  DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){public int getOldListSize(){return old.size();}public int getNewListSize(){return next.size();}public boolean areItemsTheSame(int a,int b){return old.get(a).key.equals(next.get(b).key);}public boolean areContentsTheSame(int a,int b){WorkTimeline.Row x=old.get(a),y=next.get(b);return x.last==y.last&&x.count==y.count&&Objects.equals(x.message,y.message);}});
  rows=next;workTurns.clear();for(WorkTimeline.Row row:rows)if(row.header)workTurns.add(row.turn);diff.dispatchUpdatesTo(this);
 }
 @Override public int getItemViewType(int position){return rows.get(position).header?1:rows.get(position).work()?2:0;}
 static final class Holder extends RecyclerView.ViewHolder {
  LinearLayout bubble,images,actions;TextView text,role,status;WorkSurface surface;LinearLayout line;TextView toolName,chevron,header;Button document;FadeTextView preview;
  Holder(View v){super(v);}
 }
 private int dp(android.content.Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 private TextView text(android.content.Context c,int sp){TextView t=new TextView(c);t.setTextSize(sp);t.setTextColor(c.getColor(R.color.text_primary));return t;}
 @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){
  android.content.Context c=parent.getContext();if(markdown==null)markdown=new MarkdownRenderer(c,this::link);
  if(type==0){View v=LayoutInflater.from(c).inflate(R.layout.item_message,parent,false);Holder h=new Holder(v);h.bubble=v.findViewById(R.id.messageBubble);h.text=v.findViewById(R.id.messageText);h.role=v.findViewById(R.id.messageRoleLabel);h.images=v.findViewById(R.id.messageImages);h.status=v.findViewById(R.id.messageLocalStatus);h.actions=v.findViewById(R.id.messageLocalActions);v.findViewById(R.id.toolToggle).setVisibility(View.GONE);v.findViewById(R.id.messageToolName).setVisibility(View.GONE);return h;}
  FrameLayout root=new FrameLayout(c);root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));root.setPadding(dp(c,16),0,dp(c,16),0);Holder h=new Holder(root);h.surface=new WorkSurface(c);h.surface.setPadding(dp(c,12),0,dp(c,12),0);root.addView(h.surface,new FrameLayout.LayoutParams(-1,-2));
  if(type==1){h.header=text(c,12);h.header.setId(R.id.workHeader);h.header.setGravity(Gravity.CENTER_VERTICAL);h.header.setMinHeight(dp(c,44));h.header.setPadding(dp(c,2),0,dp(c,2),0);h.surface.addView(h.header,new LinearLayout.LayoutParams(-1,-2));ExpressiveMotion.press(h.header);return h;}
  h.line=new LinearLayout(c);h.line.setId(R.id.toolToggle);h.line.setGravity(Gravity.CENTER_VERTICAL);h.line.setMinimumHeight(dp(c,44));h.line.setClickable(true);h.line.setFocusable(true);h.line.setBackgroundResource(android.R.drawable.list_selector_background);
  h.toolName=text(c,12);h.toolName.setMaxWidth(dp(c,120));h.toolName.setSingleLine(true);h.toolName.setEllipsize(android.text.TextUtils.TruncateAt.END);h.toolName.setTypeface(android.graphics.Typeface.MONOSPACE);h.line.addView(h.toolName,new LinearLayout.LayoutParams(-2,-2));
  h.preview=new FadeTextView(c);h.preview.setId(R.id.workArguments);h.preview.setTextSize(12);h.preview.setTextColor(c.getColor(R.color.text_secondary));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-2,1);pp.leftMargin=dp(c,10);h.line.addView(h.preview,pp);
  h.chevron=text(c,12);h.line.addView(h.chevron,new LinearLayout.LayoutParams(dp(c,16),-2));h.surface.addView(h.line,new LinearLayout.LayoutParams(-1,-2));
  h.text=text(c,13);h.text.setId(R.id.messageText);h.text.setPadding(0,dp(c,6),0,dp(c,10));h.text.setTextIsSelectable(true);h.surface.addView(h.text,new LinearLayout.LayoutParams(-1,-2));
  h.document=new com.google.android.material.button.MaterialButton(c,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);h.document.setTextSize(12);h.document.setText("Открыть Markdown");h.surface.addView(h.document,new LinearLayout.LayoutParams(-1,dp(c,44)));
  h.images=new LinearLayout(c);h.images.setOrientation(LinearLayout.VERTICAL);h.surface.addView(h.images,new LinearLayout.LayoutParams(-1,-2));return h;
 }
 @Override public void onBindViewHolder(@NonNull Holder h,int position,@NonNull List<Object> payloads){if(!payloads.isEmpty()&&rows.get(position).work()){WorkTimeline.Row row=rows.get(position);h.surface.shape(row.header,row.last,row.turn.equals(active));if(row.header)header(h,row);return;}onBindViewHolder(h,position);}
 @Override public void onBindViewHolder(@NonNull Holder h,int position){
  WorkTimeline.Row row=rows.get(position);android.content.Context c=h.itemView.getContext();
  if(row.header){h.surface.shape(true,row.last,row.turn.equals(active));header(h,row);h.header.setOnClickListener(v->{if(!collapsed.remove(row.turn))collapsed.add(row.turn);rebuild();});return;}
  ChatMessage m=row.message;
  if(row.work()){
   h.surface.shape(false,row.last,row.turn.equals(active));h.document.setVisibility(View.GONE);boolean tool=m.role()==ChatMessage.Role.TOOL_RESULT;h.line.setVisibility(tool?View.VISIBLE:View.GONE);
   if(tool){boolean expanded=expandedTools.contains(m.stableKey());String state="running".equals(m.toolStatus())?"выполняется":"error".equals(m.toolStatus())?"ошибка":"готово";
    h.toolName.setText(("running".equals(m.toolStatus())?"◌ ":"error".equals(m.toolStatus())?"! ":"✓ ")+m.toolName());h.preview.setText(m.preview().isEmpty()?firstLine(m.text()):m.preview());h.chevron.setText(expanded?"▾":"▸");h.line.setContentDescription((expanded?"Свернуть ":"Развернуть ")+m.toolName()+", "+state);
    h.line.setOnClickListener(v->{if(!expandedTools.remove(m.stableKey()))expandedTools.add(m.stableKey());int p=h.getBindingAdapterPosition();if(p!=-1)notifyItemChanged(p);});
    h.text.setVisibility(expanded?View.VISIBLE:View.GONE);h.text.setTypeface(android.graphics.Typeface.MONOSPACE);h.text.setText(m.text());
    if(expanded)images(h.images,m);else h.images.setVisibility(View.GONE);
    h.document.setVisibility(expanded&&!m.documentPath().isEmpty()?View.VISIBLE:View.GONE);h.document.setOnClickListener(v->link(m.documentPath()));
    if(!m.documentPath().isEmpty()){h.line.setOnLongClickListener(v->{link(m.documentPath());return true;});h.line.setContentDescription(h.line.getContentDescription()+". Удерживайте для предпросмотра Markdown");}else h.line.setOnLongClickListener(null);
   }else{h.text.setVisibility(View.VISIBLE);h.text.setTypeface(android.graphics.Typeface.DEFAULT);markdown.render(h.text,m.text());images(h.images,m);}
   return;
  }
  boolean user=m.role()==ChatMessage.Role.USER;LinearLayout.LayoutParams bp=(LinearLayout.LayoutParams)h.bubble.getLayoutParams();bp.gravity=user?Gravity.END:Gravity.START;h.bubble.setLayoutParams(bp);h.bubble.setBackgroundResource(user?R.drawable.bg_bubble_user:R.drawable.bg_bubble_assistant);
  JSONObject metric=turns.get(m.turnId());h.role.setVisibility(!user&&metric!=null&&!workTurns.contains(m.turnId())?View.VISIBLE:View.GONE);if(!user&&metric!=null){long end=metric.optLong("finishedAt"),duration=metric.optLong("generationMs"),tokens=metric.optLong("outputTokens");double elapsed=Math.max(0,(end>0?end:System.currentTimeMillis())-metric.optLong("startedAt"))/1000.0;h.role.setText(String.format(Locale.ROOT,"%.1f с · ",elapsed)+(duration>0&&tokens>0?String.format(Locale.ROOT,"%.1f ток/с",tokens*1000.0/duration):"— ток/с"));}
  LinearLayout.LayoutParams textParams=(LinearLayout.LayoutParams)h.text.getLayoutParams();textParams.topMargin=h.role.getVisibility()==View.VISIBLE?dp(c,6):0;h.text.setLayoutParams(textParams);
  h.text.setMaxWidth(Math.min(dp(c,560),c.getResources().getDisplayMetrics().widthPixels-dp(c,72)));h.text.setVisibility(m.text().isEmpty()?View.GONE:View.VISIBLE);
  if(user){h.text.setTag(R.id.markdownSource,null);h.text.setText(m.text());}else markdown.render(h.text,m.text());images(h.images,m);
  boolean local=m.localState()!=ChatMessage.LocalState.NONE;h.status.setVisibility(local?View.VISIBLE:View.GONE);if(local){h.status.setText(StatusUi.localStateLabel(c,m.localState()));h.status.setTextColor(StatusUi.localStateColor(c,m.localState()));}
  boolean retry=m.localState()==ChatMessage.LocalState.FAILED||m.localState()==ChatMessage.LocalState.UNCERTAIN;h.actions.setVisibility(retry?View.VISIBLE:View.GONE);
  h.itemView.findViewById(R.id.messageRetryButton).setOnClickListener(v->{if(listener!=null)listener.onRetry(m);});h.itemView.findViewById(R.id.messageRestoreButton).setOnClickListener(v->{if(listener!=null)listener.onRestore(m);});
 }
 private void header(Holder h,WorkTimeline.Row row){
  JSONObject stat=turns.get(row.turn);String metrics="";boolean running=row.turn.equals(active);
  if(stat!=null&&stat.optLong("startedAt")>0){long end=stat.optLong("finishedAt");long ms=Math.max(0,(end>0?end:System.currentTimeMillis())-stat.optLong("startedAt"));metrics=String.format(Locale.ROOT," · %.1f с",ms/1000.0);long duration=stat.optLong("generationMs"),tokens=stat.optLong("outputTokens");if(duration>0&&tokens>0)metrics+=String.format(Locale.ROOT," · %.1f ток/с",tokens*1000.0/duration);else metrics+=" · — ток/с";}
  h.header.setText((collapsed.contains(row.turn)?"▸ ":"▾ ")+(running?"Работает":"Работа")+metrics+"  ·  "+row.count);
  h.header.setContentDescription((collapsed.contains(row.turn)?"Развернуть работу":"Свернуть работу")+metrics+". Скорость — output tokens провайдера за измеренное время генерации, без времени инструментов");
 }
 private static String firstLine(String s){int n=s.indexOf('\n');return n<0?s:s.substring(0,n);}
 private void link(String link){if(listener!=null)listener.onDocument(link);}
 private void images(LinearLayout container,ChatMessage m){
  StringBuilder key=new StringBuilder(m.stableKey());for(ImageRef ref:m.images())key.append('\n').append(ref.url());
  if(key.toString().equals(container.getTag())){container.setVisibility(m.images().isEmpty()?View.GONE:View.VISIBLE);return;}
  container.setTag(key.toString());container.removeAllViews();container.setVisibility(m.images().isEmpty()?View.GONE:View.VISIBLE);android.content.Context c=container.getContext();
  for(int i=0;i<Math.min(3,m.images().size());i++){ImageRef ref=m.images().get(i);ImageView image=new ImageView(c);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundResource(R.drawable.bg_image_placeholder);image.setClipToOutline(true);image.setContentDescription(c.getString(R.string.cd_message_image,i+1));int width=Math.min(dp(c,480),c.getResources().getDisplayMetrics().widthPixels-dp(c,72));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,dp(c,170));p.bottomMargin=dp(c,8);container.addView(image,p);
   if(ref.url().startsWith(LOCAL_PREFIX)){Bitmap b=thumbs==null?null:thumbs.thumbnail(m.requestId(),i);if(b!=null){image.setImageBitmap(b);image.setOnClickListener(v->ImageViewer.show(c,ref.url(),b));}}
   else loader.load(ref.url(),new MediaLoader.Callback(){public void onLoaded(String url,Bitmap b){image.setImageBitmap(b);image.setOnClickListener(v->ImageViewer.show(c,ref.url(),null));}public void onFailed(String url,String error){image.setContentDescription("Изображение недоступно: "+error);}});
  }
 }
 @Override public int getItemCount(){return rows.size();}
}
