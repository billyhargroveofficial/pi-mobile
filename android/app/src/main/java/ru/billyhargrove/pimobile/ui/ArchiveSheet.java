package ru.billyhargrove.pimobile.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import org.json.*;
import java.util.*;
import ru.billyhargrove.pimobile.PiApp;
import ru.billyhargrove.pimobile.R;
import ru.billyhargrove.pimobile.net.AppExecutors;

/** Separate saved-session picker; selecting a row never sends an agent prompt. */
public final class ArchiveSheet extends BottomSheetDialog {
 public interface Listener {void resume(String id,String title);}
 private final PiApp app; private final Listener listener; private final List<JSONObject> rows=new ArrayList<>();
 private final TextView status;private final MaterialButton more;private final Rows adapter=new Rows();
 private int offset,generation;private boolean loading,closed;private String query="";
 public ArchiveSheet(Context context,PiApp app,Listener listener){
  super(context);this.app=app;this.listener=listener;
  LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);int p=dp(16);root.setPadding(p,p,p,p);
  TextView title=new TextView(context);title.setText("История диалогов");title.setTextSize(22);title.setTextColor(context.getColor(R.color.text_primary));root.addView(title);
  TextView hint=new TextView(context);hint.setText("Возобновление откроет вкладку Pi в Orca на Mac.");root.addView(hint);
  EditText search=new EditText(context);search.setHint("Название или пространство");search.setSingleLine(true);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);root.addView(search,new LinearLayout.LayoutParams(-1,dp(56)));
  search.setOnEditorActionListener((v,action,event)->{if(action!=EditorInfo.IME_ACTION_SEARCH)return false;query=search.getText().toString().trim();generation++;loading=false;rows.clear();adapter.notifyDataSetChanged();offset=0;load();return true;});
  status=new TextView(context);root.addView(status);
  RecyclerView list=new RecyclerView(context);list.setLayoutManager(new LinearLayoutManager(context));list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  more=new MaterialButton(context);more.setText("Ещё диалоги");more.setOnClickListener(v->load());root.addView(more,new LinearLayout.LayoutParams(-1,dp(48)));
  setContentView(root);root.getLayoutParams().height=(int)(context.getResources().getDisplayMetrics().heightPixels*.85f);
  setOnShowListener(d->{getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);getBehavior().setSkipCollapsed(true);});
  setOnDismissListener(d->{closed=true;generation++;});load();
 }
 private int dp(int value){return Math.round(value*getContext().getResources().getDisplayMetrics().density);}
 private void load(){
  if(loading||closed)return;loading=true;more.setEnabled(false);status.setText("Загрузка…");int version=generation,start=offset;String q=query,base=app.settings().baseUrl(),token=app.settings().token();
  AppExecutors.io().execute(()->{JSONObject data=null;String error=null;try{data=app.api().fetchArchive(base,token,start,q);}catch(Exception e){error=e.getMessage();}JSONObject result=data;String failure=error;
   AppExecutors.main(()->{if(closed||version!=generation)return;loading=false;more.setEnabled(true);if(result==null){status.setText(failure==null?"Не удалось загрузить историю":failure);more.setText("Повторить");more.setVisibility(View.VISIBLE);return;}
    JSONArray items=result.optJSONArray("sessions");if(items!=null)for(int i=0;i<items.length();i++){JSONObject row=items.optJSONObject(i);if(row!=null)rows.add(row);}offset=result.optInt("nextOffset",start+40);adapter.notifyDataSetChanged();status.setText(rows.isEmpty()?"Сохранённых закрытых диалогов нет":"Выбери диалог для возобновления");more.setText("Ещё диалоги");more.setVisibility(result.optBoolean("hasMore")?View.VISIBLE:View.GONE);
   });
  });
 }
 private final class Rows extends RecyclerView.Adapter<Holder>{
  @Override public Holder onCreateViewHolder(ViewGroup parent,int type){LinearLayout box=new LinearLayout(parent.getContext());box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(14),dp(12),dp(14));box.setMinimumHeight(dp(72));box.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(box);}
  @Override public void onBindViewHolder(Holder h,int position){JSONObject row=rows.get(position);h.title.setText(row.optString("title"));String date=row.optString("modified");if(date.length()>10)date=date.substring(0,10);h.detail.setText(row.optString("workspaceName")+" · "+date+" · "+row.optInt("messageCount")+" сообщ.");h.itemView.setOnClickListener(v->listener.resume(row.optString("id"),row.optString("title")));}
  @Override public int getItemCount(){return rows.size();}
 }
 private static final class Holder extends RecyclerView.ViewHolder{
  final TextView title,detail;Holder(LinearLayout box){super(box);title=new TextView(box.getContext());title.setTextSize(16);title.setTextColor(box.getContext().getColor(R.color.text_primary));title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);detail=new TextView(box.getContext());detail.setTextSize(12);detail.setTextColor(box.getContext().getColor(R.color.text_secondary));box.addView(title);box.addView(detail);}
 }
}
