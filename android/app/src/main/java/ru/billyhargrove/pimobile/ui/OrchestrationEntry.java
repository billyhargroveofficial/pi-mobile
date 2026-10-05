package ru.billyhargrove.pimobile.ui;

import android.app.Activity;
import android.os.*;
import android.view.View;
import com.google.android.material.button.MaterialButton;
import org.json.JSONObject;
import ru.billyhargrove.pimobile.*;
import ru.billyhargrove.pimobile.core.OrchestrationData;
import ru.billyhargrove.pimobile.net.AppExecutors;

/** Lifecycle-owned lightweight discovery; independently observes agents after the parent's turn ends. */
public final class OrchestrationEntry {
 private final Activity activity;private final MaterialButton button;private final String session;private final Handler handler=new Handler(Looper.getMainLooper());private boolean live,busy;private int generation;private final Runnable poll=this::refresh;
 public OrchestrationEntry(Activity activity,MaterialButton button,String session){this.activity=activity;this.button=button;this.session=session;button.setVisibility(View.GONE);button.setOnClickListener(v->activity.startActivity(OrchestrationActivity.intent(activity,session,"","","Orchestration")));}
 public void start(){live=true;refresh();}public void stop(){live=false;generation++;busy=false;handler.removeCallbacks(poll);}
 private void refresh(){if(!live||busy)return;PiApp app=PiApp.get(activity);if(!app.settings().hasToken())return;busy=true;int expected=generation;String base=app.settings().baseUrl(),token=app.settings().token();AppExecutors.io().execute(()->{JSONObject data=null;try{data=app.api().fetchOrchestration(base,token,session);}catch(Exception ignored){}JSONObject value=data;AppExecutors.main(()->{if(!live||expected!=generation)return;busy=false;if(value!=null)render(value);else if(button.getVisibility()==View.VISIBLE)button.setText("Orchestration · reconnecting  ›");handler.postDelayed(poll,3000);});});}
 public void render(JSONObject data){boolean any=OrchestrationData.agents(data,null).size()>0||OrchestrationData.objects(data.optJSONArray("workflows")).size()>0;button.setVisibility(any?View.VISIBLE:View.GONE);button.setText(OrchestrationData.summary(data)+"  ›");button.setContentDescription("Open orchestration: "+OrchestrationData.summary(data));}
}
