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

/** Plain assistant progress messages and independently expandable tool segments. */
public final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
 public interface LocalThumbProvider { Bitmap thumbnail(String requestId,int index); }
 public interface Listener {void onRetry(ChatMessage message);void onRestore(ChatMessage message);default void onDocument(String path){} }
 public static final String LOCAL_PREFIX="local:";
 public static ImageRef localRef(int index,String mime){return new ImageRef(LOCAL_PREFIX+index,mime);}
 private final MediaLoader loader;private final LocalThumbProvider thumbs;private final Listener listener;
 private final TranscriptPresentation presentation=new TranscriptPresentation();
 private List<WorkTimeline.Row> rows=new ArrayList<>();
 private final Set<String> collapsed=new HashSet<>();
 private final Set<String> animatedGroups=new HashSet<>();
 private Map<String,List<ChatMessage>> bodies=new HashMap<>();
 private MarkdownRenderer markdown;
 public MessageAdapter(MediaLoader loader,LocalThumbProvider thumbs,Listener listener){this.loader=loader;this.thumbs=thumbs;this.listener=listener;}
 public void close(){if(markdown!=null)markdown.close();}
 public void metadata(JSONObject frame){presentation.metadata(frame);rebuild();}
 public void sessionStatus(SessionStatus status){presentation.sessionStatus(status);rebuild();}
 public int size(){return rows.size();}
 public String keyAt(int pos){return pos>=0&&pos<rows.size()?rows.get(pos).key:"";}
 public int positionOf(String key){for(int i=0;i<rows.size();i++)if(rows.get(i).key.equals(key))return i;return -1;}
 public void submit(List<ChatMessage> source){presentation.submit(source);rebuild();}
 private void rebuild(){
  List<WorkTimeline.Row> old=rows,next=new ArrayList<>();Map<String,List<ChatMessage>> oldBodies=bodies,nextBodies=new HashMap<>();
  collapsed.clear();
  for(TranscriptPresentation.Item item:presentation.items()){
   WorkTimeline.Row row=item.getRow();next.add(row);
   if(row.header){if(item.getExpanded())nextBodies.put(row.group,item.getTools());else{collapsed.add(row.group);if(oldBodies.containsKey(row.group))animatedGroups.add(row.group);}}
  }
  DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){public int getOldListSize(){return old.size();}public int getNewListSize(){return next.size();}public boolean areItemsTheSame(int a,int b){return old.get(a).key.equals(next.get(b).key);}public boolean areContentsTheSame(int a,int b){WorkTimeline.Row x=old.get(a),y=next.get(b);return x.last==y.last&&x.count==y.count&&Objects.equals(x.message,y.message)&&Objects.equals(oldBodies.get(x.group),nextBodies.get(y.group));}});
  bodies=nextBodies;rows=next;diff.dispatchUpdatesTo(this);
 }
 @Override public int getItemViewType(int position){return rows.get(position).header?1:0;}
 static final class Holder extends RecyclerView.ViewHolder {
  String boundGroup="";LinearLayout bubble,images,actions;TextView text,role,status;WorkSurface surface;TextView header;WorkLogView log;
  Holder(View v){super(v);}
 }
 private int dp(android.content.Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 private TextView text(android.content.Context c,int sp){TextView t=new TextView(c);t.setTextSize(sp);t.setTextColor(c.getColor(R.color.text_primary));return t;}
 @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){
  android.content.Context c=parent.getContext();if(markdown==null)markdown=new MarkdownRenderer(c,this::link);
  if(type==0){View v=LayoutInflater.from(c).inflate(R.layout.item_message,parent,false);Holder h=new Holder(v);h.bubble=v.findViewById(R.id.messageBubble);h.text=v.findViewById(R.id.messageText);h.role=v.findViewById(R.id.messageRoleLabel);h.images=v.findViewById(R.id.messageImages);h.status=v.findViewById(R.id.messageLocalStatus);h.actions=v.findViewById(R.id.messageLocalActions);v.findViewById(R.id.toolToggle).setVisibility(View.GONE);v.findViewById(R.id.messageToolName).setVisibility(View.GONE);return h;}
  FrameLayout root=new FrameLayout(c);root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));root.setPadding(dp(c,16),dp(c,4),dp(c,16),dp(c,4));Holder h=new Holder(root);h.surface=new WorkSurface(c);h.surface.setPadding(dp(c,12),0,dp(c,12),0);root.addView(h.surface,new FrameLayout.LayoutParams(-1,-2));
  h.header=text(c,12);h.header.setId(R.id.workHeader);h.header.setGravity(Gravity.CENTER_VERTICAL);h.header.setMinHeight(dp(c,44));h.header.setPadding(dp(c,2),0,dp(c,2),0);h.surface.addView(h.header,new LinearLayout.LayoutParams(-1,-2));
  h.log=new WorkLogView(c,markdown);h.surface.addView(h.log,new LinearLayout.LayoutParams(-1,-2));return h;
 }
 @Override public void onBindViewHolder(@NonNull Holder h,int position,@NonNull List<Object> payloads){onBindViewHolder(h,position);}
 @Override public void onBindViewHolder(@NonNull Holder h,int position){
  WorkTimeline.Row row=rows.get(position);android.content.Context c=h.itemView.getContext();
  if(row.header){
   h.surface.shape(true,true,false);boolean open=!collapsed.contains(row.group);header(h,row);
   boolean animate=animatedGroups.remove(row.group)&&row.group.equals(h.boundGroup);h.boundGroup=row.group;
   if(open)h.log.submit(row.group,bodies.get(row.group));h.log.expanded(open,animate);
   h.header.setOnClickListener(v->{presentation.toggle(row.group);animatedGroups.add(row.group);rebuild();});return;
  }
  ChatMessage m=row.message;
  boolean user=m.role()==ChatMessage.Role.USER;LinearLayout.LayoutParams bp=(LinearLayout.LayoutParams)h.bubble.getLayoutParams();bp.gravity=user?Gravity.END:Gravity.START;h.bubble.setLayoutParams(bp);if(user)h.bubble.setBackground(BubbleColors.background(c,BubbleColors.color(c)));else h.bubble.setBackgroundResource(R.drawable.bg_bubble_assistant);h.text.setTextColor(user?BubbleColors.foreground(BubbleColors.color(c)):c.getColor(R.color.text_primary));
  h.role.setVisibility(View.GONE);
  LinearLayout.LayoutParams textParams=(LinearLayout.LayoutParams)h.text.getLayoutParams();textParams.topMargin=h.role.getVisibility()==View.VISIBLE?dp(c,6):0;h.text.setLayoutParams(textParams);
  h.text.setMaxWidth(Math.min(dp(c,560),c.getResources().getDisplayMetrics().widthPixels-dp(c,72)));h.text.setVisibility(m.text().isEmpty()?View.GONE:View.VISIBLE);
  if(user){h.text.setTag(R.id.markdownSource,null);h.text.setText(m.text());}else markdown.render(h.text,m.text());images(h.images,m);
  boolean local=m.localState()!=ChatMessage.LocalState.NONE;boolean accepted=user&&(m.localState()==ChatMessage.LocalState.ACCEPTED||m.localState()==ChatMessage.LocalState.NONE);h.status.setVisibility(local||accepted?View.VISIBLE:View.GONE);h.status.setGravity(Gravity.END);h.status.setTextSize(11);if(accepted){h.status.setText("✓✓");h.status.setTextColor(BubbleColors.foreground(BubbleColors.color(c)));h.status.setContentDescription("Accepted by Pi");}else if(local){h.status.setText(StatusUi.localStateLabel(c,m.localState()));h.status.setTextColor(StatusUi.localStateColor(c,m.localState()));h.status.setContentDescription(h.status.getText());}
  boolean retry=m.localState()==ChatMessage.LocalState.FAILED||m.localState()==ChatMessage.LocalState.UNCERTAIN;h.actions.setVisibility(retry?View.VISIBLE:View.GONE);
  h.itemView.findViewById(R.id.messageRetryButton).setOnClickListener(v->{if(listener!=null)listener.onRetry(m);});h.itemView.findViewById(R.id.messageRestoreButton).setOnClickListener(v->{if(listener!=null)listener.onRestore(m);});
 }
 private void header(Holder h,WorkTimeline.Row row){
  h.header.setText("Tools · "+row.count);
  h.header.setContentDescription(collapsed.contains(row.group)?"Expand tools":"Collapse tools");
  androidx.core.view.ViewCompat.setStateDescription(h.header,collapsed.contains(row.group)?"Collapsed":"Expanded");
 }
 private static String firstLine(String s){int n=s.indexOf('\n');return n<0?s:s.substring(0,n);}
 private void link(String link){if(listener!=null)listener.onDocument(link);}
 private void images(LinearLayout container,ChatMessage m){
  StringBuilder key=new StringBuilder(m.stableKey());for(ImageRef ref:m.images())key.append('\n').append(ref.url());
  if(key.toString().equals(container.getTag())){container.setVisibility(m.images().isEmpty()?View.GONE:View.VISIBLE);return;}
  container.setTag(key.toString());container.removeAllViews();container.setVisibility(m.images().isEmpty()?View.GONE:View.VISIBLE);android.content.Context c=container.getContext();
  for(int i=0;i<Math.min(3,m.images().size());i++){ImageRef ref=m.images().get(i);ImageView image=new ImageView(c);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundResource(R.drawable.bg_image_placeholder);image.setClipToOutline(true);image.setContentDescription(c.getString(R.string.cd_message_image,i+1));int width=Math.min(dp(c,240),c.getResources().getDisplayMetrics().widthPixels-dp(c,72));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,dp(c,220));p.bottomMargin=dp(c,8);container.addView(image,p);
   if(ref.url().startsWith(LOCAL_PREFIX)){int index=i;try{index=Integer.parseInt(ref.url().substring(LOCAL_PREFIX.length()));}catch(NumberFormatException ignored){}Bitmap b=thumbs==null?null:thumbs.thumbnail(m.requestId(),index);if(b!=null){fitThumbnail(image,b);image.setOnClickListener(v->ImageViewer.show(c,ref.url(),b));}}
   else loader.load(ref.url(),new MediaLoader.Callback(){public void onLoaded(String url,Bitmap b){fitThumbnail(image,b);image.setOnClickListener(v->ImageViewer.show(c,ref.url(),b));}public void onFailed(String url,String error){image.setContentDescription("Image unavailable: "+error);}});
  }
 }
 private void fitThumbnail(ImageView image,Bitmap bitmap){
  android.content.Context c=image.getContext();int maxWidth=Math.min(dp(c,240),c.getResources().getDisplayMetrics().widthPixels-dp(c,72)),maxHeight=dp(c,280);
  float scale=Math.min((float)maxWidth/bitmap.getWidth(),(float)maxHeight/bitmap.getHeight());
  android.view.ViewGroup.LayoutParams p=image.getLayoutParams();p.width=Math.max(1,Math.round(bitmap.getWidth()*scale));p.height=Math.max(1,Math.round(bitmap.getHeight()*scale));image.setLayoutParams(p);image.setImageBitmap(bitmap);
 }
 @Override public int getItemCount(){return rows.size();}
}
