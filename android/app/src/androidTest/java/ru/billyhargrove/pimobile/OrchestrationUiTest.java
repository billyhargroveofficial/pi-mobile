package ru.billyhargrove.pimobile;
import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ActivityScenario;import androidx.test.ext.junit.runners.AndroidJUnit4;import androidx.test.platform.app.InstrumentationRegistry;import androidx.test.uiautomator.*;
import org.junit.Test;import org.junit.runner.RunWith;import org.json.*;
@RunWith(AndroidJUnit4.class)
public class OrchestrationUiTest {
 private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
 private void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();device.waitForIdle(1000);}
 private JSONObject fixture()throws Exception {return new JSONObject("{\"available\":true,\"liveAvailable\":true,\"workflows\":[{\"id\":\"wf_design\",\"title\":\"Polish the mobile experience\",\"description\":\"Independent review, implementation and verification\",\"status\":\"running\",\"agentCount\":4,\"completed\":2,\"phases\":[{\"title\":\"Explore\",\"status\":\"completed\",\"agentCount\":2,\"completed\":2},{\"title\":\"Build\",\"status\":\"running\",\"agentCount\":1,\"completed\":0},{\"title\":\"Verify\",\"status\":\"queued\",\"agentCount\":1,\"completed\":0}]}],\"agents\":[{\"id\":\"a\",\"workflowId\":\"wf_design\",\"name\":\"Map the event stream\",\"phase\":\"Explore\",\"status\":\"completed\",\"model\":\"openai-codex/gpt-6-sol\",\"canInspect\":true},{\"id\":\"b\",\"workflowId\":\"wf_design\",\"name\":\"Review navigation\",\"phase\":\"Explore\",\"status\":\"completed\",\"model\":\"openai-codex/gpt-6-sol\",\"canInspect\":true},{\"id\":\"c\",\"workflowId\":\"wf_design\",\"name\":\"Build workflow cards\",\"description\":\"Live status, phase hierarchy and nested conversations\",\"phase\":\"Build\",\"status\":\"running\",\"model\":\"openai-codex/gpt-6-sol\",\"thinkingLevel\":\"high\",\"outputTokens\":1840,\"canInspect\":true},{\"id\":\"d\",\"workflowId\":\"wf_design\",\"name\":\"Verify on a narrow screen\",\"phase\":\"Verify\",\"status\":\"queued\",\"canInspect\":false},{\"id\":\"standalone\",\"name\":\"Check accessibility\",\"status\":\"running\",\"description\":\"Touch targets, contrast and large text\",\"model\":\"openai-codex/gpt-6-sol\",\"canInspect\":true}]} ");}
 @Test public void workflowCardsShowRealPhasesAndOpenDrillDown()throws Exception{JSONObject data=fixture();try(ActivityScenario<OrchestrationActivity> scenario=ActivityScenario.launch(OrchestrationActivity.intent(context,"ui-fixture","","","Orchestration"))){scenario.onActivity(a->{a.stopUpdates();a.render(data);});idle();assertTrue(device.hasObject(By.text("Polish the mobile experience")));assertTrue(device.hasObject(By.textContains("2 of 4 agents done")));device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"orchestration-overview.png"));device.findObject(By.descStartsWith("Workflow: Polish")).click();assertTrue(device.wait(Until.hasObject(By.text("Polish the mobile experience")),3000));device.pressBack();}}
 @Test public void workflowDetailShowsActiveCompletedAndQueuedAgents()throws Exception{JSONObject data=fixture();try(ActivityScenario<OrchestrationActivity> scenario=ActivityScenario.launch(OrchestrationActivity.intent(context,"ui-fixture","workflow","wf_design","Polish the mobile experience"))){scenario.onActivity(a->{a.stopUpdates();a.render(data);});idle();assertTrue(device.hasObject(By.text("Map the event stream")));assertTrue(device.hasObject(By.text("Build workflow cards")));device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"orchestration-agents.png"));device.findObject(By.descStartsWith("Agent: Build workflow cards")).click();assertTrue(device.wait(Until.hasObject(By.text("Build workflow cards")),3000));device.pressBack();}}
 @Test public void childAgentsAreScopedToTheirParentAndOpenReadOnly()throws Exception{JSONObject data=fixture();data.getJSONArray("agents").put(new JSONObject().put("id","child").put("parentId","c").put("workflowId","wf_design").put("name","Inspect tool output").put("status","running").put("model","openai-codex/gpt-6-sol").put("canInspect",true));data.getJSONArray("agents").put(new JSONObject().put("id","other-child").put("parentId","b").put("name","Other branch").put("status","completed"));try(ActivityScenario<OrchestrationActivity> scenario=ActivityScenario.launch(OrchestrationActivity.intent(context,"ui-fixture","","","Child agents").putExtra("parentAgent","c"))){scenario.onActivity(a->{a.stopUpdates();a.render(data);});idle();assertTrue(device.hasObject(By.text("Inspect tool output")));assertFalse(device.hasObject(By.text("Other branch")));device.findObject(By.descStartsWith("Agent: Inspect tool output")).click();assertTrue(device.wait(Until.hasObject(By.text("Inspect tool output")),3000));device.pressBack();}}
 @Test public void agentConversationIsReadOnlyAndCanLoadEarlierActivity()throws Exception{JSONObject data=new JSONObject("{\"agent\":{\"id\":\"c\",\"name\":\"Build workflow cards\",\"status\":\"running\",\"model\":\"openai-codex/gpt-6-sol\",\"thinkingLevel\":\"high\",\"outputTokens\":1840},\"childCount\":1,\"hasMore\":true,\"before\":120,\"messages\":[{\"id\":\"u\",\"role\":\"user\",\"text\":\"Build a clear, live workflow view.\"},{\"id\":\"p\",\"role\":\"assistant\",\"phase\":\"work\",\"text\":\"I found the phase events. Now connecting the live cards.\"},{\"id\":\"t\",\"role\":\"toolResult\",\"toolName\":\"read\",\"toolStatus\":\"done\",\"preview\":\"{\\\"path\\\":\\\"src/workflow.ts\\\"}\",\"text\":\"Read 84 lines.\"},{\"id\":\"p2\",\"role\":\"assistant\",\"phase\":\"work\",\"text\":\"The workflow is updating without interrupting its agents.\"}]}");try(ActivityScenario<OrchestrationActivity> scenario=ActivityScenario.launch(OrchestrationActivity.intent(context,"ui-fixture","agent","c","Build workflow cards"))){scenario.onActivity(a->{a.stopUpdates();a.renderAgent(data,false);assertNull(a.findViewById(R.id.composerInput));assertNull(a.findViewById(R.id.stopButton));});idle();assertTrue(device.hasObject(By.textContains("I found the phase events")));assertTrue(device.hasObject(By.res(context.getPackageName(),"agentLoadOlder")));assertTrue(device.hasObject(By.res(context.getPackageName(),"agentChildren")));device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"orchestration-conversation.png"));JSONObject earlier=new JSONObject(data.toString()).put("hasMore",false).put("before",0).put("messages",new JSONArray().put(new JSONObject().put("id","old").put("role","user").put("text","Earlier task context")));scenario.onActivity(a->a.renderAgent(earlier,true));assertTrue(device.wait(Until.gone(By.res(context.getPackageName(),"agentLoadOlder")),3000));}}

 private JSONObject agentPage(String status,JSONArray messages)throws Exception {
  return new JSONObject().put("agent",new JSONObject().put("id","compose-agent").put("status",status).put("model","openai-codex/gpt-6-sol"))
   .put("source","live").put("messages",messages).put("hasMore",true).put("before",100);
 }
 private JSONObject line(String id,String role,String text)throws Exception {return new JSONObject().put("id",id).put("role",role).put("text",text).put("turnId","compose-turn").put("phase","work");}
 @Test public void composeToolGroupsSettleOnceAndPreserveManualExpansion()throws Exception {
  JSONArray lines=new JSONArray().put(line("u","user","Inspect the transcript"))
   .put(line("t1","toolResult","First output").put("toolName","read").put("preview","first.kt"))
   .put(line("p","assistant","Progress remains visible"))
   .put(line("t2","toolResult","Second output").put("toolName","read").put("preview","second.kt"));
  JSONObject running=agentPage("running",lines);
  try(ActivityScenario<OrchestrationActivity> scenario=ActivityScenario.launch(OrchestrationActivity.intent(context,"ui-fixture","agent","compose-agent","Compose tools"))){
   scenario.onActivity(a->{a.stopUpdates();a.renderAgent(running,false);});idle();
   assertEquals(2,device.findObjects(By.res(context.getPackageName(),"workLogList")).size());
   assertTrue(device.hasObject(By.text("Progress remains visible")));
   JSONObject done=agentPage("completed",lines);
   scenario.onActivity(a->a.renderAgent(done,false));idle();
   assertTrue("Tool collapse animation did not finish",device.wait(Until.gone(By.res(context.getPackageName(),"workLogList")),3000));
   assertTrue(device.hasObject(By.text("Progress remains visible")));
   device.findObjects(By.desc("Expand tools")).get(0).click();idle();
   assertEquals(1,device.findObjects(By.res(context.getPackageName(),"workLogList")).size());
   scenario.onActivity(a->a.renderAgent(done,false));idle();
   assertEquals("Repeated completed snapshots must preserve manual expansion",1,device.findObjects(By.res(context.getPackageName(),"workLogList")).size());
   assertTrue(device.hasObject(By.text("first.kt")));
   assertFalse(device.hasObject(By.text("second.kt")));
   device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"compose-tool-disclosure.png"));
  }
 }
 @Test public void composeHistoryAndLiveUpdatesKeepTheReadersAnchor()throws Exception {
  JSONArray lines=new JSONArray();for(int i=0;i<50;i++)lines.put(line("context-"+i,"user",String.format(java.util.Locale.ROOT,"Context %02d",i)));
  JSONObject page=agentPage("running",lines);
  try(ActivityScenario<OrchestrationActivity> scenario=ActivityScenario.launch(OrchestrationActivity.intent(context,"ui-fixture","agent","compose-agent","Compose history"))){
   scenario.onActivity(a->{a.stopUpdates();a.renderAgent(page,false);});idle();
   UiObject2 list=device.findObject(By.res(context.getPackageName(),"orchestrationList"));assertNotNull(list);
   list.scroll(Direction.UP,0.8f,300);
   java.util.concurrent.atomic.AtomicBoolean moving=new java.util.concurrent.atomic.AtomicBoolean(true);
   for(int i=0;i<50&&moving.get();i++){
    scenario.onActivity(a->{try{java.lang.reflect.Field field=OrchestrationActivity.class.getDeclaredField("transcriptList");field.setAccessible(true);moving.set(((androidx.compose.foundation.lazy.LazyListState)field.get(a)).isScrollInProgress());}catch(ReflectiveOperationException error){throw new RuntimeException(error);}});
    if(moving.get())android.os.SystemClock.sleep(100);
   }
   assertFalse("Reader scroll did not settle",moving.get());idle();
   java.util.List<UiObject2> visible=device.findObjects(By.textStartsWith("Context "));assertFalse(visible.isEmpty());
   UiObject2 anchor=visible.get(visible.size()/2);String text=anchor.getText();int top=anchor.getVisibleBounds().top-device.findObject(By.res(context.getPackageName(),"orchestrationList")).getVisibleBounds().top;
   JSONArray earlier=new JSONArray();for(int i=0;i<10;i++)earlier.put(line("old-"+i,"user","Older context "+i).put("turnId","older-turn"));
   JSONObject history=agentPage("running",earlier).put("hasMore",false);
   scenario.onActivity(a->a.renderAgent(history,true));idle();assertTrue(device.wait(Until.gone(By.res(context.getPackageName(),"agentLoadOlder")),3000));
   UiObject2 afterHistory=device.findObject(By.text(text));assertNotNull("Visible message lost after prepend: "+text,afterHistory);
   assertEquals("Prepending history moves the reading position",top,afterHistory.getVisibleBounds().top-device.findObject(By.res(context.getPackageName(),"orchestrationList")).getVisibleBounds().top,2);
   for(int i=50;i<60;i++)lines.put(line("context-"+i,"user",String.format(java.util.Locale.ROOT,"Context %02d",i)));
   scenario.onActivity(a->a.renderAgent(page,false));idle();
   UiObject2 afterLive=device.findObject(By.text(text));assertNotNull("Live update forced the reader to the bottom",afterLive);
   assertEquals(top,afterLive.getVisibleBounds().top-device.findObject(By.res(context.getPackageName(),"orchestrationList")).getVisibleBounds().top,2);
   JSONObject saved=agentPage("completed",new JSONArray().put(line("saved","user","Saved source replaces live"))).put("source","saved-log");
   scenario.onActivity(a->a.renderAgent(saved,false));idle();
   assertTrue(device.wait(Until.hasObject(By.text("Saved source replaces live")),3000));assertTrue(device.wait(Until.gone(By.text(text)),3000));
  }
 }
 @Test public void composeToolLogIsBoundedAndScrollsWithinItsGroup()throws Exception {
  JSONArray lines=new JSONArray().put(line("u","user","Read files"));
  for(int i=0;i<30;i++)lines.put(line("tool-"+i,"toolResult","Output "+i).put("toolName","read").put("preview","file-"+i+".kt"));
  JSONObject page=agentPage("running",lines);
  try(ActivityScenario<OrchestrationActivity> scenario=ActivityScenario.launch(OrchestrationActivity.intent(context,"ui-fixture","agent","compose-agent","Bounded tool log"))){
   scenario.onActivity(a->{a.stopUpdates();a.renderAgent(page,false);});idle();
   UiObject2 log=device.findObject(By.res(context.getPackageName(),"workLogList"));assertNotNull(log);
   float density=context.getResources().getDisplayMetrics().density;
   assertTrue("Tool log exceeds 200dp",log.getVisibleBounds().height()<=200*density+2);
   assertTrue("New tool log does not follow its tail",device.wait(Until.hasObject(By.text("file-29.kt")),3000));
   log=device.findObject(By.res(context.getPackageName(),"workLogList"));
   android.graphics.Rect bounds=log.getVisibleBounds();
   device.swipe(bounds.centerX(),bounds.top+20,bounds.centerX(),bounds.bottom-20,80);idle();
   device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"compose-tools-scrolled.png"));
   java.util.List<UiObject2> previews=device.findObjects(By.textStartsWith("file-"));
   String anchor=null;for(UiObject2 node:previews)if(node.getVisibleBounds().height()>0){anchor=node.getText();break;}
   assertNotNull("No tool previews visible after scrolling",anchor);
   assertFalse("Inner log did not scroll away from tail",anchor.equals("file-29.kt"));
   device.findObject(By.desc("Collapse tools")).click();
   assertTrue(device.wait(Until.gone(By.res(context.getPackageName(),"workLogList")),3000));
   device.findObject(By.desc("Expand tools")).click();idle();
   assertTrue("Reopening tools loses the inner reading position: "+anchor,device.wait(Until.hasObject(By.text(anchor)),3000));
   for(int i=0;i<50;i++)lines.put(line("following-"+i,"user","Following context "+i));
   scenario.onActivity(a->a.renderAgent(page,false));idle();
   // UiObject2.scroll gestures at the node's center, where the nested tool log
   // can consume the gesture and report its own boundary. Use the transcript's
   // padding gutter and verify the actual destination rather than that return value.
   for(int i=0;i<20&&!device.hasObject(By.text("Following context 49"));i++){
    android.graphics.Rect viewport=device.findObject(By.res(context.getPackageName(),"orchestrationList")).getVisibleBounds();
    int gutter=viewport.left+Math.round(12*density),inset=Math.round(24*density);
    device.swipe(gutter,viewport.bottom-inset,gutter,viewport.top+inset,40);idle();
   }
   assertTrue("Outer transcript did not reach its final message",device.hasObject(By.text("Following context 49")));
   assertFalse("Tool block did not leave the viewport",device.hasObject(By.res(context.getPackageName(),"workLogList")));
   for(int i=0;i<20&&!device.hasObject(By.text("Read files"));i++){
    android.graphics.Rect viewport=device.findObject(By.res(context.getPackageName(),"orchestrationList")).getVisibleBounds();
    int gutter=viewport.left+Math.round(12*density),inset=Math.round(24*density);
    device.swipe(gutter,viewport.top+inset,gutter,viewport.bottom-inset,40);idle();
   }
   device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"compose-tools-returned.png"));
   assertTrue("Reader did not reach the original tool block",device.hasObject(By.text("Read files")));
   assertNotNull(device.wait(Until.findObject(By.res(context.getPackageName(),"workLogList")),3000));
   assertTrue("Returning to an offscreen tool block loses the inner reading position: "+anchor,device.wait(Until.hasObject(By.text(anchor)),3000));
  }
 }

 @Test public void composeReaderKeepsNavigationAndPagingReachable()throws Exception {
  JSONObject page=agentPage("running",new JSONArray().put(line("u","user","Review the workflow"))
   .put(line("p","assistant","The transcript remains a read-only view of the original agent."))).put("childCount",3);
  try(ActivityScenario<OrchestrationActivity> scenario=ActivityScenario.launch(OrchestrationActivity.intent(context,"ui-fixture","agent","compose-agent","A longer agent title for narrow displays"))){
   scenario.onActivity(a->{a.stopUpdates();a.renderAgent(page,false);});idle();
   for(String description:new String[]{"Back","Refresh orchestration"}){
    UiObject2 button=device.findObject(By.desc(description));assertNotNull(description,button);
    android.graphics.Rect bounds=button.getVisibleBounds();assertTrue(description,bounds.width()>0&&bounds.height()>0);
    assertTrue(description,bounds.left>=0&&bounds.right<=device.getDisplayWidth());
   }
   android.graphics.Rect children=device.findObject(By.res(context.getPackageName(),"agentChildren")).getVisibleBounds();
   android.graphics.Rect older=device.findObject(By.res(context.getPackageName(),"agentLoadOlder")).getVisibleBounds();
   android.graphics.Rect transcript=device.findObject(By.res(context.getPackageName(),"orchestrationList")).getVisibleBounds();
   assertTrue(children.height()>0&&older.height()>0);assertTrue(children.bottom<=older.top);
   assertTrue(older.bottom<=transcript.top);assertTrue(transcript.height()>0);
   assertTrue(transcript.bottom<=device.getDisplayHeight());
   device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"compose-reader-stress.png"));
  }
 }
}
