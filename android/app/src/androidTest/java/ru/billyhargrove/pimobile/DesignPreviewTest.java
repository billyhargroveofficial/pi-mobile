package ru.billyhargrove.pimobile;
import static org.junit.Assert.*;
import android.content.Context;import android.view.View;import android.graphics.Rect;
import androidx.test.core.app.ActivityScenario;import androidx.test.ext.junit.runners.AndroidJUnit4;import androidx.test.platform.app.InstrumentationRegistry;import androidx.test.uiautomator.*;
import org.junit.Before;import org.junit.Test;import org.junit.runner.RunWith;import org.json.*;
import ru.billyhargrove.pimobile.ui.*;
@RunWith(AndroidJUnit4.class)
public class DesignPreviewTest {
 private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
 @Before public void isolateSyntheticFixtures(){androidx.test.uiautomator.Configurator.getInstance().setWaitForIdleTimeout(1000);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->PiApp.get(context).client().disconnect());}
 private void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();device.waitForIdle(1000);}
 private void doubleTap(Rect bounds){
  long start=android.os.SystemClock.uptimeMillis()-130;
  for(int tap=0;tap<2;tap++){
   long down=start+tap*100;
   for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}){
    long time=down+(action==android.view.MotionEvent.ACTION_UP?30:0);
    android.view.MotionEvent event=android.view.MotionEvent.obtain(down,time,action,bounds.centerX(),bounds.centerY(),0);
    event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
    // Fixed touch timestamps keep a 70ms inter-tap gap even if the emulator stalls.
    assertTrue("Touch injection failed",InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(event,false));event.recycle();
   }
  }
  idle();
 }
 private float imageScale(ActivityScenario<ChatActivity> scenario){float[] value={0};scenario.onActivity(a->{androidx.fragment.app.DialogFragment viewer=(androidx.fragment.app.DialogFragment)a.getSupportFragmentManager().findFragmentByTag("image-viewer");android.widget.ImageView image=viewer.getDialog().findViewById(R.id.zoomImage);float[] matrix=new float[9];image.getImageMatrix().getValues(matrix);value[0]=matrix[android.graphics.Matrix.MSCALE_X];});return value[0];}
 private UiObject2 usageRow(String id){
  UiObject2 item=device.wait(Until.findObject(By.res(context.getPackageName(),id)),5000);
  assertNotNull("Usage row must be reachable: "+id,item);return item;
 }
 private JSONObject quota(String provider,String window,double used)throws Exception{return new JSONObject().put("provider",provider).put("status","ok").put("updatedAt",System.currentTimeMillis()).put("windows",new JSONArray().put(new JSONObject().put("name",window).put("usedPercent",used).put("resetsAt",System.currentTimeMillis()+4L*86400000)));}
 @Test public void usageAndSettingsHaveHierarchyAndNoOverlap()throws Exception {
  JSONObject cursor=quota("cursor","monthly",100);cursor.getJSONArray("windows").put(new JSONObject().put("name","Cursor Models").put("usedPercent",55)).put(new JSONObject().put("name","Other Models").put("usedPercent",2));
  JSONObject data=new JSONObject().put("providers",new JSONArray().put(quota("codex","weekly",36)).put(cursor).put(new JSONObject().put("provider","grok").put("status","error").put("windows",new JSONArray())));
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{CatalogFixture.install(a,new ru.billyhargrove.pimobile.core.Catalog(java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.Workspace("preview","harness-space","/workspace")),java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.Session("preview-session","Pi Mobile · design pass","/workspace","preview","preview-terminal",true,ru.billyhargrove.pimobile.core.SessionStatus.RUNNING,"openai-codex/gpt-6-astra")),null));CatalogFixture.usage(a).render(data);});idle();
   assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"usageCodex")),5000));
   Rect first=usageRow("usageCodex").getVisibleBounds(),second=usageRow("usageCursor").getVisibleBounds(),third=usageRow("usageGrok").getVisibleBounds();assertEquals(first.left,second.left);assertEquals(first.right,second.right);assertEquals(first.left,third.left);assertEquals(first.right,third.right);assertTrue(first.bottom<=second.top);assertTrue(second.bottom<=third.top);assertTrue(first.height()>=28*context.getResources().getDisplayMetrics().density-1);assertTrue(device.findObject(By.res(context.getPackageName(),"usageCards")).getVisibleBounds().height()>=48*context.getResources().getDisplayMetrics().density-1);if(context.getResources().getConfiguration().fontScale<=1.05f)assertTrue("Compact usage must not dominate the chat list",device.findObject(By.res(context.getPackageName(),"usageCards")).getVisibleBounds().height()<=108*context.getResources().getDisplayMetrics().density);
   device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"usage-redesign.png"));
   device.findObject(By.res(context.getPackageName(),"usageCards")).click();assertTrue(device.wait(Until.hasObject(By.text("Usage")),3000));for(int i=0;i<12&&!device.hasObject(By.text("Cursor Models"));i++){device.findObject(By.res(context.getPackageName(),"usageDetails")).scroll(Direction.DOWN,.25f);idle();}assertTrue("All reported Cursor limits remain reachable in unified details",device.hasObject(By.text("Cursor Models")));device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"usage-details.png"));device.pressBack();assertTrue("Usage details must dismiss before Settings becomes actionable",device.wait(Until.gone(By.res(context.getPackageName(),"usageDetails")),5000));idle();
   UiObject2 settings=device.wait(Until.findObject(By.res(context.getPackageName(),"settingsButton")),5000);assertNotNull("Settings remains reachable after closing Usage",settings);settings.click();assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"connectPanel")),3000));
   float touchHeight=48*context.getResources().getDisplayMetrics().density;
   for(int i=0;i<6;i++){
    UiObject2 bubbleRow=device.findObject(By.res(context.getPackageName(),"bubbleColorSettings")),updateRow=device.findObject(By.res(context.getPackageName(),"checkUpdates"));
    if(bubbleRow!=null&&updateRow!=null&&bubbleRow.getVisibleBounds().height()>=touchHeight&&updateRow.getVisibleBounds().height()>=touchHeight)break;
    Rect bounds=device.findObject(By.res(context.getPackageName(),"connectPanel")).getVisibleBounds();device.swipe(bounds.centerX(),bounds.bottom-40,bounds.centerX(),bounds.top+40,30);idle();
   }
   assertTrue(device.hasObject(By.res(context.getPackageName(),"bubbleColorSettings")));assertTrue(device.hasObject(By.res(context.getPackageName(),"checkUpdates")));idle();
   Rect update=device.findObject(By.res(context.getPackageName(),"checkUpdates")).getVisibleBounds(),bubble=device.findObject(By.res(context.getPackageName(),"bubbleColorSettings")).getVisibleBounds();assertTrue("Settings rows must not overlap",update.bottom<=bubble.top||bubble.bottom<=update.top);assertTrue(update.height()>40);assertTrue(bubble.height()>40);device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"settings-redesign.png"));device.pressBack();
  }
 }
 @Test public void tierPickerIsExplicitAndDoesNotSendUntilApply()throws Exception {
  JSONObject config=new JSONObject("{\"model\":\"openai-codex/gpt-6-astra\",\"thinkingLevel\":\"high\",\"serviceTier\":\"standard\",\"models\":[{\"provider\":\"openai-codex\",\"id\":\"gpt-6-astra\",\"name\":\"GPT-6 Astra\",\"thinkingLevels\":[\"low\",\"medium\",\"high\"],\"serviceTiers\":[\"standard\",\"fast\"]}]}");
  final String[] sent={null};final ModelSettingsSheet[] sheet={null};
  try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(ChatActivity.intent(context,"design-preview","Design preview",false))){scenario.onActivity(ChatFixture::prepareLoading);scenario.onActivity(a->{sheet[0]=new ModelSettingsSheet(a,config,true,(provider,model,effort,tier)->sent[0]=tier);sheet[0].show();});assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"tierFast")),3000));device.findObject(By.res(context.getPackageName(),"tierFast")).click();idle();assertNull(sent[0]);device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"tier-picker.png"));device.findObject(By.res(context.getPackageName(),"applyModelButton")).click();idle();assertEquals("fast",sent[0]);scenario.onActivity(a->sheet[0].dismiss());}
 }
 @Test public void workingHeaderShowsActualModelEffortAndTier()throws Exception {
  JSONObject config=new JSONObject("{\"model\":\"openai-codex/gpt-6-astra\",\"thinkingLevel\":\"low\",\"serviceTier\":\"standard\",\"serviceTiers\":[\"standard\",\"fast\"],\"execution\":{\"model\":\"gpt-6-astra\",\"thinkingLevel\":\"high\",\"serviceTier\":\"fast\",\"tierConfirmed\":true}}");JSONObject snapshot=new JSONObject("{\"sessionId\":\"header-preview\",\"status\":\"running\",\"messages\":[{\"id\":\"user\",\"role\":\"user\",\"text\":\"Polish the mobile interface.\"}]}");
  try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(ChatActivity.intent(context,"header-preview","Pi Mobile · design pass",false))){scenario.onActivity(ChatFixture::prepareLoading);scenario.onActivity(a->{a.onConfiguration("header-preview",config);a.onSnapshot(ru.billyhargrove.pimobile.core.SnapshotParser.parse(snapshot));});idle();assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"chatStatusText")),5000));String detail=device.findObject(By.res(context.getPackageName(),"chatStatusText")).getText();assertTrue(detail,detail.contains("gpt-6-astra"));assertTrue(detail,detail.contains(" · High · "));assertTrue(detail,detail.contains("Fast"));assertFalse(detail,detail.contains("requested"));assertFalse(detail.contains("\n"));device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"chat-model-header.png"));}
 }
 @Test public void attachmentThumbnailOpensAndZoomsFromWrappedContext()throws Exception {
  final android.graphics.Bitmap[] thumbnail={null};
  try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(ChatActivity.intent(context,"attachment-preview","Attachment preview",false))){scenario.onActivity(ChatFixture::prepareLoading);
   scenario.onActivity(a->{android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(400,900,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(b);canvas.drawColor(android.graphics.Color.DKGRAY);android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(android.graphics.Color.WHITE);paint.setTextSize(28);canvas.drawText("Screenshot preview",24,60,paint);for(int i=0;i<8;i++){paint.setColor(i%2==0?0xff315de8:0xff666666);canvas.drawRoundRect(24,100+i*90,376,170+i*90,16,16,paint);}
    thumbnail[0]=b;ru.billyhargrove.pimobile.features.chat.ChatSession chat=ChatFixture.install(a,"attachment-preview",false,java.util.Collections.emptyList());ChatFixture.text(chat,"Attached screenshot");chat.prepared(java.util.Collections.singletonList(new ru.billyhargrove.pimobile.media.Attachment(new ru.billyhargrove.pimobile.core.ImagePayload(new byte[]{1},"image/png"),b,"screenshot.png")));chat.send(ru.billyhargrove.pimobile.core.CommandBuilder.Behavior.FOLLOW_UP);chat.onAck(new ru.billyhargrove.pimobile.core.Ack("request-1","attachment-preview",true,""));
   });idle();
   UiObject2 thumb=device.wait(Until.findObject(By.desc(context.getString(R.string.cd_message_image,1))),5000);
   assertNotNull("Attachment thumbnail did not bind",thumb);
   Rect bounds=thumb.getVisibleBounds();assertTrue("Portrait thumbnail became square",bounds.height()>bounds.width());
   assertEquals(400f/900f,(float)bounds.width()/bounds.height(),0.02f);
   device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"attachment-thumbnail.png"));
   thumb.click();assertTrue("Wrapped context did not open viewer",device.wait(Until.hasObject(By.res(context.getPackageName(),"zoomImage")),3000));
   UiObject2 full=device.findObject(By.res(context.getPackageName(),"zoomImage"));idle();float fit=imageScale(scenario);
   full.pinchOpen(0.5f);idle();float enlarged=imageScale(scenario);assertTrue("Pinch did not increase scale",enlarged>fit);
   full.pinchClose(0.8f);idle();assertTrue("Pinch close did not reduce scale",imageScale(scenario)<enlarged);
   Rect screen=full.getVisibleBounds();
   if(imageScale(scenario)>fit*1.1f){doubleTap(screen);assertEquals("Double tap did not reset zoom",fit,imageScale(scenario),0.01f);android.os.SystemClock.sleep(350);}
   float beforeDouble=imageScale(scenario);doubleTap(screen);
   assertTrue("Double tap did not increase scale: fit="+fit+", before="+beforeDouble+", after="+imageScale(scenario),imageScale(scenario)>fit);
   device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"attachment-fullscreen.png"));
   device.findObject(By.res(context.getPackageName(),"closeImageButton")).click();
   assertTrue("Viewer did not close",device.wait(Until.gone(By.res(context.getPackageName(),"zoomImage")),3000));
   scenario.onActivity(a->{if(thumbnail[0]!=null)thumbnail[0].recycle();});
  }
 }
 @Test public void microphoneHasLiveWaveformAndPrivateFile()throws Exception {
  InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),android.Manifest.permission.RECORD_AUDIO);
  final DictationRecorder[] recorder={null};final java.io.File[] result={null};final String[] error={null};java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
  try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(ChatActivity.intent(context,"voice-preview","Voice preview",false))){scenario.onActivity(ChatFixture::prepareLoading);
   try{
    scenario.onActivity(a->recorder[0]=new DictationRecorder(a,file->{result[0]=file;done.countDown();},message->{error[0]=message;done.countDown();}));
    assertTrue("Microphone waveform did not appear",device.wait(Until.hasObject(By.res(context.getPackageName(),"voiceWaveform")),5000));
    assertTrue("PCM recording did not reach two seconds",device.wait(Until.hasObject(By.text(java.util.regex.Pattern.compile("00:(0[2-9]|[1-5][0-9])|0[1-9]:[0-5][0-9]"))),5000));
    device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"voice-recording.png"));
    device.findObject(By.text("Finish")).click();
    assertTrue("Recording did not finish",done.await(5,java.util.concurrent.TimeUnit.SECONDS));
    assertNull(error[0]);assertNotNull(result[0]);assertTrue(result[0].length()>=32000);
    assertTrue(result[0].getCanonicalPath().startsWith(context.getCacheDir().getCanonicalPath()));assertEquals(600,DictationRecorder.MAX_SECONDS);
   }finally{
    scenario.onActivity(a->{if(recorder[0]!=null)recorder[0].cancel();});
    if(result[0]!=null)result[0].delete();
   }
  }
 }
}
