package ru.billyhargrove.pimobile;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.view.View;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** UI-only checks: never send a prompt or stop any real Pi session. */
@RunWith(AndroidJUnit4.class)
public class ExpressiveUiTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    @Before public void isolateSyntheticFixtures() {
        androidx.test.uiautomator.Configurator.getInstance().setWaitForIdleTimeout(1000);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> PiApp.get(context).client().disconnect());
    }
    private Intent chat() { return ChatActivity.intent(context, "ui-test-no-agent", "Проверка дизайна", false); }
    private void idle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); device.waitForIdle(1000); }


    private androidx.test.uiautomator.UiObject2 node(String id) {
        androidx.test.uiautomator.UiObject2 node=device.wait(Until.findObject(By.res(context.getPackageName(),id)),5000);
        assertNotNull("Missing Compose node: "+id,node);return node;
    }
    private float density(){return context.getResources().getDisplayMetrics().density;}
    private Rect settledBounds(String id) {
        Rect previous=new Rect();int stable=0;
        for(int i=0;i<50;i++) {
            Rect next=node(id).getVisibleBounds();
            stable=next.equals(previous)?stable+1:0;previous=next;
            if(stable>=3)return next;
            android.os.SystemClock.sleep(100);
        }
        fail("Bounds did not settle: "+id);return previous;
    }
    private View root(ChatActivity a){return a.findViewById(android.R.id.content);}
    private void keyboard(ActivityScenario<ChatActivity> scenario) {
        node("composerInput").click();
        java.util.concurrent.atomic.AtomicBoolean shown=new java.util.concurrent.atomic.AtomicBoolean();
        for(int i=0;i<50&&!shown.get();i++){scenario.onActivity(a->{androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(root(a));shown.set(insets!=null&&insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));});if(!shown.get())android.os.SystemClock.sleep(100);}
        assertTrue("IME must actually be shown",shown.get());idle();
    }

    @Test public void catalogHasSeparateHistoryAction() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {scenario.onActivity(a->CatalogFixture.install(a,ru.billyhargrove.pimobile.core.Catalog.empty()));
            idle();
            Rect bounds=node("historyButton").getVisibleBounds();
            assertTrue(node("mainRoot").getVisibleBounds().contains(bounds));
            assertTrue(bounds.height()>=48*density()-1);
            assertNotNull(node("historyButton").findObject(By.text("History")));
        }
    }

    @Test public void usesExpressiveMaterialComponents() {
    try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);idle();
        assertEquals("Dictation",node("sendButton").getContentDescription());
        assertTrue(node("effortButton").getContentDescription().startsWith("Effort:"));
        assertEquals("Attach file",node("attachImageButton").getContentDescription());
    }
}

    @Test public void composerHasFourEqualCircularIcons() {
    try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);idle();
        Rect send=node("sendButton").getVisibleBounds();
        for(String id:new String[]{"attachImageButton","deliveryButton","effortButton","sendButton"}){
            Rect bounds=node(id).getVisibleBounds();assertEquals(bounds.width(),bounds.height());assertEquals(send.width(),bounds.width());assertEquals(send.centerY(),bounds.centerY());
        }
        Rect back=node("backButton").getVisibleBounds();assertEquals(back.width(),back.height());
    }
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(a->CatalogFixture.install(a,ru.billyhargrove.pimobile.core.Catalog.empty()));idle();for(String id:new String[]{"settingsButton","refreshButton"}){Rect bounds=node(id).getVisibleBounds();assertEquals(bounds.width(),bounds.height());assertTrue(bounds.width()>=48*density()-1);}}
}

    @Test public void compactComposerAndWorkingBadge() throws Exception {
    try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);idle();assertEquals("Writing row + action row",96*density(),node("composerEditor").getVisibleBounds().height(),1);
        org.json.JSONObject frame=new org.json.JSONObject().put("sessionId","ui-test-no-agent").put("activeTurnId","turn").put("turns",new org.json.JSONArray().put(new org.json.JSONObject().put("id","turn").put("startedAt",System.currentTimeMillis()-1588000)));
        scenario.onActivity(a->{a.onTimelineMeta(frame);assertEquals("turn",ChatFixture.state(a).getMetadata().optString("activeTurnId"));});assertTrue(device.wait(Until.hasObject(By.textStartsWith("Working · 26m")),5000));device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"chat-working-duration.png"));assertFalse(device.hasObject(By.res(context.getPackageName(),"historySpinner")));
    }
}

    @Test public void composerActionsFitAndHave48dpTouchTargets() {
    try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);idle();Rect root=node("chatRoot").getVisibleBounds();
        for(String id:new String[]{"sendButton","attachImageButton","effortButton","deliveryButton"}){Rect bounds=node(id).getVisibleBounds();assertTrue(root.contains(bounds));assertTrue(bounds.height()>=48*density()-1);assertTrue(bounds.width()>=48*density()-1);}
    }
}

    @Test public void keyboardDoesNotCoverComposer() {
    try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);idle();keyboard(scenario);final int[] keyboardTop={0};
        scenario.onActivity(a->{View root=root(a);androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(root);int[] xy=new int[2];root.getLocationOnScreen(xy);keyboardTop[0]=xy[1]+root.getHeight()-insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;});
        assertTrue("Composer must remain above IME",node("sendButton").getVisibleBounds().bottom<=keyboardTop[0]);assertTrue(node("composerEditor").getVisibleBounds().height()>48*density());device.pressBack();
    }
}

    private java.util.List<ru.billyhargrove.pimobile.core.ChatMessage> toolMessages(int count) {
        java.util.List<ru.billyhargrove.pimobile.core.ChatMessage> rows=new java.util.ArrayList<>();
        for(int i=0;i<count;i++)rows.add(new ru.billyhargrove.pimobile.core.ChatMessage("tool:"+i,ru.billyhargrove.pimobile.core.ChatMessage.Role.TOOL_RESULT,"command "+i,null,"bash",ru.billyhargrove.pimobile.core.ChatMessage.LocalState.NONE,"","done"));
        return rows;
    }

    @Test public void progressStaysOpenWhileToolsCollapseOnFinish() throws Exception {
    try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);
        java.util.List<ru.billyhargrove.pimobile.core.ChatMessage> source=new java.util.ArrayList<>();source.add(toolMessages(1).get(0).withPresentation("turn","work","",""));source.add(ru.billyhargrove.pimobile.core.ChatMessage.remote("progress",ru.billyhargrove.pimobile.core.ChatMessage.Role.ASSISTANT,"Проверяю исправление",null,null).withPresentation("turn","work","",""));source.add(toolMessages(2).get(1).withPresentation("turn","work","",""));
        scenario.onActivity(a->ChatFixture.show(a,source));idle();assertEquals(2,device.findObjects(By.res(context.getPackageName(),"workLogList")).size());assertTrue(device.hasObject(By.text("Проверяю исправление")));device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"progress-active.png"));
        org.json.JSONObject done=new org.json.JSONObject().put("sessionId","ui-test-no-agent").put("activeTurnId","").put("turns",new org.json.JSONArray().put(new org.json.JSONObject().put("id","turn").put("finishedAt",123)));
        scenario.onActivity(a->a.onTimelineMeta(done));idle();assertTrue(device.wait(Until.gone(By.res(context.getPackageName(),"workLogList")),5000));assertTrue(device.hasObject(By.text("Проверяю исправление")));assertFalse(device.hasObject(By.desc("Expand progress")));device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"progress-settled.png"));device.findObjects(By.desc("Expand tools")).get(0).click();idle();assertEquals(1,device.findObjects(By.res(context.getPackageName(),"workLogList")).size());scenario.onActivity(a->a.onTimelineMeta(done));idle();assertEquals(1,device.findObjects(By.res(context.getPackageName(),"workLogList")).size());
    }
}

    @Test public void longAnswerStaysAtBottomAndUpdatesDoNotPullReaderUp() throws Exception {
    org.json.JSONArray messages=new org.json.JSONArray().put(new org.json.JSONObject().put("id","u").put("role","user").put("text","Вопрос").put("turnId","turn")).put(new org.json.JSONObject().put("id","a").put("role","assistant").put("text",String.join("\n",java.util.Collections.nCopies(140,"Строка ответа"))).put("turnId","turn"));org.json.JSONObject data=new org.json.JSONObject().put("sessionId","ui-test-no-agent").put("messages",messages).put("status","idle");
    try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);scenario.onActivity(a->a.onSnapshot(ru.billyhargrove.pimobile.core.SnapshotParser.parse(data)));idle();scenario.onActivity(a->assertFalse("Long answer END must align",ChatFixture.list(a).getCanScrollForward()));
        Rect bounds=node("messageList").getVisibleBounds();device.swipe(bounds.centerX(),bounds.top+200,bounds.centerX(),bounds.bottom-200,25);idle();final int[] top={0};scenario.onActivity(a->{assertTrue(ChatFixture.list(a).getCanScrollForward());top[0]=ChatFixture.list(a).getFirstVisibleItemScrollOffset();a.onSnapshot(ru.billyhargrove.pimobile.core.SnapshotParser.parse(data));});idle();scenario.onActivity(a->assertEquals(top[0],ChatFixture.list(a).getFirstVisibleItemScrollOffset()));
    }
}

    @Test public void pendingReceiptsSurviveReentryWithoutAutomaticReplay(){ru.billyhargrove.pimobile.store.PendingMessages store=new ru.billyhargrove.pimobile.store.PendingMessages(context,"mock-host","mock-outbox");try{store.save(java.util.Collections.singletonList(ru.billyhargrove.pimobile.core.ChatMessage.local("r","Сохранённый вопрос",null,ru.billyhargrove.pimobile.core.ChatMessage.LocalState.SENDING)));ru.billyhargrove.pimobile.store.PendingMessages reopened=new ru.billyhargrove.pimobile.store.PendingMessages(context,"mock-host","mock-outbox");assertEquals("Сохранённый вопрос",reopened.load().get(0).text());assertEquals(ru.billyhargrove.pimobile.core.ChatMessage.LocalState.UNCERTAIN,reopened.load().get(0).localState());}finally{store.save(java.util.Collections.emptyList());}}

    @Test public void workBubbleFitsSmallLogsAndCapsLargeLogs() {
    try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);
        scenario.onActivity(a->ChatFixture.show(a,toolMessages(2)));idle();assertTrue(node("workLogList").getVisibleBounds().height()<=50*density());assertTrue(node("workLogList").getVisibleBounds().height()>=40*density()-1);
        scenario.onActivity(a->ChatFixture.show(a,toolMessages(40)));assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"workToolCount").text("40")),5000));assertEquals(200*density(),settledBounds("workLogList").height(),1);
        scenario.onActivity(a->ChatFixture.show(a,toolMessages(1)));assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"workToolCount").text("1")),5000));assertTrue(settledBounds("workLogList").height()<=30*density());
    }
}

    @Test public void workLogRespondsToFingerScrollAndHeaderTap() {
    try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);
        scenario.onActivity(a->ChatFixture.show(a,toolMessages(40)));Rect bounds=settledBounds("workLogList");String before=device.findObjects(By.res(context.getPackageName(),"workArguments")).get(0).getText();
        device.swipe(bounds.centerX(),bounds.top+50,bounds.centerX(),bounds.bottom-50,35);settledBounds("workLogList");String after=device.findObjects(By.res(context.getPackageName(),"workArguments")).get(0).getText();assertNotEquals("Inner tools must actually scroll",before,after);
        node("workHeader").click();assertTrue(device.wait(Until.gone(By.res(context.getPackageName(),"workLogList")),5000));node("workHeader").click();assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"workLogList")),5000));
    }
}

    @Test public void effortPopupRespondsToFingerDrag() throws Exception {
        org.json.JSONObject model=new org.json.JSONObject("{\"thinkingLevels\":[\"low\",\"medium\",\"high\"]}");final String[] sent={null};
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);
            idle();Rect anchor=node("effortButton").getVisibleBounds();scenario.onActivity(a->new ru.billyhargrove.pimobile.ui.EffortPopup(root(a),()->anchor,model,"low",true,()->{},value->sent[0]=value,"standard",null));assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"quickEffortSlider")),5000));idle();
            Rect r=device.findObject(By.res(context.getPackageName(),"quickEffortSlider")).getVisibleBounds();device.swipe(r.left+70,r.centerY(),r.right-70,r.centerY(),35);idle();assertEquals("high",sent[0]);node("closeEffortPanel").click();
        }
    }

    @Test public void toolRowsStayDenseAndNeverExpand() {
    try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);
        scenario.onActivity(a->ChatFixture.show(a,java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.ChatMessage("tool:one",ru.billyhargrove.pimobile.core.ChatMessage.Role.TOOL_RESULT,"partial-one",null,"bash",ru.billyhargrove.pimobile.core.ChatMessage.LocalState.NONE,"","running"))));idle();
        assertTrue(node("toolLine").getVisibleBounds().height()<=22*density()+1);assertFalse(node("toolLine").isClickable());node("toolLine").click();assertFalse(device.hasObject(By.res(context.getPackageName(),"messageText")));
        scenario.onActivity(a->ChatFixture.show(a,java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.ChatMessage("tool:one",ru.billyhargrove.pimobile.core.ChatMessage.Role.TOOL_RESULT,"partial-two",null,"bash",ru.billyhargrove.pimobile.core.ChatMessage.LocalState.NONE,"","running"))));assertTrue(device.wait(Until.hasObject(By.text("partial-two")),5000));node("toolLine").click();assertFalse(device.hasObject(By.res(context.getPackageName(),"messageText")));
    }
}

    @Test public void imageOpensFullscreenAndCanBeZoomed() {
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chat())) {scenario.onActivity(ChatFixture::prepareLoading);
            scenario.onActivity(a -> {android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(80,80,android.graphics.Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.GREEN);ru.billyhargrove.pimobile.ui.ImageViewer.show(new android.view.ContextThemeWrapper(a,R.style.Theme_PiMobile),"local:test",b);});
            assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"zoomImage")),5000));
            androidx.test.uiautomator.UiObject2 image=device.findObject(By.res(context.getPackageName(),"zoomImage"));
            image.pinchOpen(0.5f);image.pinchClose(0.5f);
            device.findObject(By.res(context.getPackageName(),"closeImageButton")).click();
            assertTrue(device.wait(Until.gone(By.res(context.getPackageName(),"zoomImage")),5000));
        }
    }

    @Test public void speedometerModelRowOpensNextPickerWithoutApplying() throws Exception {
        org.json.JSONObject model=new org.json.JSONObject("{\"id\":\"a\",\"name\":\"Model A\",\"thinkingLevels\":[\"low\",\"high\"]}");final boolean[] opened={false};final String[] sent={null};
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {scenario.onActivity(ChatFixture::prepareLoading);idle();Rect anchor=node("effortButton").getVisibleBounds();scenario.onActivity(a->new ru.billyhargrove.pimobile.ui.EffortPopup(root(a),()->anchor,model,"low",true,()->opened[0]=true,value->sent[0]=value,"standard",null));assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"popupModelButton")),5000));device.findObject(By.res(context.getPackageName(),"popupModelButton")).click();idle();assertTrue(opened[0]);assertNull(sent[0]);node("closeEffortPanel").click();}
    }

    @Test public void paletteUsesFixedNeutralColors() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(a->CatalogFixture.install(a,ru.billyhargrove.pimobile.core.Catalog.empty()));scenario.onActivity(a->{boolean dark=(a.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;assertEquals(android.graphics.Color.parseColor(dark?"#FFFFFF":"#171717"),a.getColor(R.color.accent));assertEquals(android.graphics.Color.parseColor(dark?"#000000":"#FFFFFF"),a.getColor(R.color.bg));assertEquals("Assistant content stays on the canvas",a.getColor(R.color.bg),a.getColor(R.color.bubble_assistant));});}
    }

    @Test public void modelListChangesEffortRangeAndWaitsForDone() throws Exception {
        org.json.JSONObject config=new org.json.JSONObject("{\"model\":\"test/a\",\"thinkingLevel\":\"high\",\"models\":[{\"provider\":\"test\",\"id\":\"a\",\"name\":\"Model A\",\"thinkingLevels\":[\"off\",\"high\"]},{\"provider\":\"test\",\"id\":\"b\",\"name\":\"Model B\",\"thinkingLevels\":[\"medium\",\"xhigh\"]}]}");
        final String[] sent={null};final ru.billyhargrove.pimobile.ui.ModelSettingsSheet[] sheet={null};
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {scenario.onActivity(ChatFixture::prepareLoading);
            scenario.onActivity(a->{sheet[0]=new ru.billyhargrove.pimobile.ui.ModelSettingsSheet(a,config,true,(p,m,e)->sent[0]=p+"/"+m+":"+e);sheet[0].show();});
            assertTrue(device.wait(Until.hasObject(By.descStartsWith("Model B")),5000));device.findObject(By.descStartsWith("Model B")).click();idle();
            assertNotNull(node("modelSelector"));scenario.onActivity(a->{ru.billyhargrove.pimobile.ui.EffortSlider slider=sheet[0].findViewById(R.id.effortSelector);assertEquals("medium",slider.value());assertEquals(1,slider.getMax());slider.setProgress(1);});
            assertNull(sent[0]);device.findObject(By.res(context.getPackageName(),"applyModelButton")).click();idle();assertEquals("test/b:xhigh",sent[0]);scenario.onActivity(a->sheet[0].dismiss());
        }
    }

    @Test public void quickEffortKeepsKeyboardAndCommitsOnRelease() throws Exception {
        org.json.JSONObject model=new org.json.JSONObject("{\"thinkingLevels\":[\"low\",\"medium\",\"high\"]}");
        final String[] sent={null};final ru.billyhargrove.pimobile.ui.EffortPopup[] popup={null};
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {scenario.onActivity(ChatFixture::prepareLoading);
            idle();keyboard(scenario);
            java.util.concurrent.atomic.AtomicBoolean shown=new java.util.concurrent.atomic.AtomicBoolean();
            for(int i=0;i<50&&!shown.get();i++){scenario.onActivity(a->{androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(root(a));shown.set(insets!=null&&insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));});if(!shown.get())android.os.SystemClock.sleep(100);}
            assertTrue(shown.get());Rect anchor=node("effortButton").getVisibleBounds();scenario.onActivity(a->popup[0]=new ru.billyhargrove.pimobile.ui.EffortPopup(root(a),()->anchor,model,"low",true,()->{},value->sent[0]=value,"standard",null));
            assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"quickEffortSlider")),5000));idle();assertNull(sent[0]);
            scenario.onActivity(a->assertTrue(androidx.core.view.ViewCompat.getRootWindowInsets(root(a)).isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())));
            device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"effort-popup-keyboard.png"));
            Rect slider=device.findObject(By.res(context.getPackageName(),"quickEffortSlider")).getVisibleBounds();device.click(slider.right-30,slider.centerY());idle();assertEquals("high",sent[0]);assertNotNull(node("quickEffortSlider"));assertTrue(popup[0].isShowing());node("closeEffortPanel").click();assertTrue(device.wait(Until.gone(By.res(context.getPackageName(),"quickEffortSlider")),3000));device.pressBack();
        }
    }

    @Test public void modelSheetSendsSelectedModelAndEffortOnlyOnApply() throws Exception {
        org.json.JSONObject config=new org.json.JSONObject("{\"model\":\"test/a\",\"thinkingLevel\":\"high\",\"models\":[{\"provider\":\"test\",\"id\":\"a\",\"thinkingLevels\":[\"off\",\"high\"]}]}");
        final String[] sent={null};
        final ru.billyhargrove.pimobile.ui.ModelSettingsSheet[] sheet=new ru.billyhargrove.pimobile.ui.ModelSettingsSheet[1];
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {scenario.onActivity(ChatFixture::prepareLoading);
            scenario.onActivity(a->{sheet[0]=new ru.billyhargrove.pimobile.ui.ModelSettingsSheet(a,config,true,(provider,model,effort)->sent[0]=provider+"/"+model+":"+effort);sheet[0].show();});
            assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"applyModelButton")),5000));
            assertNull(sent[0]);
            device.findObject(By.res(context.getPackageName(),"applyModelButton")).click();idle();assertEquals("test/a:high",sent[0]);
            scenario.onActivity(a->sheet[0].dismiss());
        }
    }

    @Test public void markdownAndLatexRenderNativelyInPreview() {
        final ru.billyhargrove.pimobile.ui.MarkdownPreview[] preview={null};
        java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {scenario.onActivity(ChatFixture::prepareLoading);
            scenario.onActivity(a->{preview[0]=new ru.billyhargrove.pimobile.ui.MarkdownPreview(a,"example.md","## Заголовок\n\n**Жирный** и `code`.\n\n$$\n\\frac{a}{b}=x^2\n$$\n",link->{});preview[0].show();});
            for(int attempt=0;attempt<80&&!ready.get();attempt++){
                scenario.onActivity(a->{android.widget.TextView text=preview[0].findViewById(R.id.documentText);if(text!=null&&text.getText() instanceof android.text.Spanned){android.text.Spanned s=(android.text.Spanned)text.getText();io.noties.markwon.image.AsyncDrawableSpan[] spans=s.getSpans(0,s.length(),io.noties.markwon.image.AsyncDrawableSpan.class);ready.set(spans.length>0&&spans[0].getDrawable().hasResult());}});
                if(!ready.get())android.os.SystemClock.sleep(100);
            }
            assertTrue("LaTeX must produce an actual drawable, not raw source",ready.get());
            scenario.onActivity(a->{android.widget.TextView text=preview[0].findViewById(R.id.documentText);assertFalse(text.getText().toString().contains("**Жирный**"));preview[0].dismiss();});
        }
    }

    @Test public void archiveStartsWithSkeletonsAndComposeSearch() throws Exception {
        java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
        final ru.billyhargrove.pimobile.ui.ArchiveSheet[] sheet={null};
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{CatalogFixture.install(a,ru.billyhargrove.pimobile.core.Catalog.empty());sheet[0]=new ru.billyhargrove.pimobile.ui.ArchiveSheet(a,(offset,query)->{release.await(10,java.util.concurrent.TimeUnit.SECONDS);return new org.json.JSONObject();},(id,title)->fail("Loading must not open a session"));sheet[0].show();});
            idle();assertNotNull(node("archiveSearch"));assertTrue(device.findObjects(By.res(context.getPackageName(),"archiveSkeleton")).size()>=4);assertNotNull(node("archiveDateHeader"));
            scenario.onActivity(a->sheet[0].dismiss());
        } finally { release.countDown(); }
    }

    @Test public void archiveSkeletonHasExactlyTheLoadedRowGeometry() throws Exception {
        java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);final ru.billyhargrove.pimobile.ui.ArchiveSheet[] sheet={null};final Rect[] before={new Rect(),new Rect()},after={new Rect(),new Rect()};
        org.json.JSONArray rows=new org.json.JSONArray();for(int i=0;i<6;i++)rows.put(new org.json.JSONObject().put("id","mock-"+i).put("title","Диалог "+i).put("workspaceName","Workspace").put("messageCount",12).put("modified",java.time.Instant.now().toString()));org.json.JSONObject data=new org.json.JSONObject().put("sessions",rows).put("hasMore",false);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(a->CatalogFixture.install(a,ru.billyhargrove.pimobile.core.Catalog.empty()));
            scenario.onActivity(a->{sheet[0]=new ru.billyhargrove.pimobile.ui.ArchiveSheet(a,(offset,query)->{if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("test timeout");return data;},(id,title)->{});sheet[0].show();});idle();
            before[0]=node("archiveDateHeader").getVisibleBounds();before[1]=node("archiveSkeleton").getVisibleBounds();release.countDown();assertTrue(device.wait(Until.hasObject(By.text("Диалог 0")),5000));idle();
            after[0]=node("archiveDateHeader").getVisibleBounds();after[1]=node("archiveRow").getVisibleBounds();for(int i=0;i<2;i++)assertEquals("Header/card must not jump after loading",before[i],after[i]);scenario.onActivity(a->sheet[0].dismiss());
        }finally{release.countDown();}
    }

    @Test public void emptyWorkspacePlusRequiresConfirmation() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {scenario.onActivity(a->CatalogFixture.install(a,ru.billyhargrove.pimobile.core.Catalog.empty()));
            idle();
            scenario.onActivity(a->{a.onConnectionState(ru.billyhargrove.pimobile.core.ConnectionState.CONNECTED,"");a.onCatalog(new ru.billyhargrove.pimobile.core.Catalog(java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.Workspace("mock-workspace","Пустое пространство","/mock")),null,null));});idle();
            assertNotNull(node("catalogList"));
            assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"newSessionButton")),5000));device.findObject(By.res(context.getPackageName(),"newSessionButton")).click();
            assertTrue(device.wait(Until.hasObject(By.text("New session")),5000));assertTrue(device.hasObject(By.textContains("Пустое пространство")));device.findObject(By.text("Cancel")).click();
        }
    }

    @Test public void swipeLiveSessionWarnsBeforeInterrupting() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {scenario.onActivity(a->CatalogFixture.install(a,ru.billyhargrove.pimobile.core.Catalog.empty()));
            idle();
            scenario.onActivity(a->{a.onConnectionState(ru.billyhargrove.pimobile.core.ConnectionState.CONNECTED,"");a.onCatalog(new ru.billyhargrove.pimobile.core.Catalog(java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.Workspace("mock-workspace","Тест","/mock")),java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.Session("mock-session","Тестовая задача","/mock","mock-workspace","mock-terminal",true,ru.billyhargrove.pimobile.core.SessionStatus.RUNNING,"mock/model")),null));});idle();
            assertTrue(device.wait(Until.hasObject(By.text("Тестовая задача")),5000));Rect bounds=device.findObject(By.text("Тестовая задача")).getVisibleBounds();device.swipe(device.getDisplayWidth()-80,bounds.centerY(),60,bounds.centerY(),18);
            assertTrue(device.wait(Until.hasObject(By.text("Interrupt Pi and close the tab?")),5000));assertTrue(device.hasObject(By.textContains("history will remain on disk")));device.findObject(By.text("Cancel")).click();
        }
    }

    @Test public void swipeArchiveDeletesOnlyAfterConfirmation() throws Exception {
        final java.util.concurrent.atomic.AtomicInteger deletes=new java.util.concurrent.atomic.AtomicInteger();final ru.billyhargrove.pimobile.ui.ArchiveSheet[] sheet={null};org.json.JSONObject data=new org.json.JSONObject().put("sessions",new org.json.JSONArray().put(new org.json.JSONObject().put("id","mock-delete").put("title","Удаляемый диалог").put("modified",java.time.Instant.now().toString())));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {scenario.onActivity(a->CatalogFixture.install(a,ru.billyhargrove.pimobile.core.Catalog.empty()));
            scenario.onActivity(a->{sheet[0]=new ru.billyhargrove.pimobile.ui.ArchiveSheet(a,(offset,query)->data,(id,title)->fail("Swipe must not resume"),(id,success,failure)->{assertEquals("mock-delete",id);deletes.incrementAndGet();});sheet[0].show();});
            assertTrue(device.wait(Until.hasObject(By.text("Удаляемый диалог")),5000));idle();Rect bounds=device.findObject(By.text("Удаляемый диалог")).getVisibleBounds();device.swipe(device.getDisplayWidth()-80,bounds.centerY(),60,bounds.centerY(),18);
            assertTrue(device.wait(Until.hasObject(By.text("Delete session from disk?")),5000));assertEquals(0,deletes.get());device.findObject(By.text("Delete")).click();idle();assertEquals(1,deletes.get());scenario.onActivity(a->sheet[0].dismiss());
        }
    }

    @Test public void deliveryPickerChangesModeWithoutSendingPrompt() {
    try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);idle();node("deliveryButton").click();assertTrue(device.wait(Until.hasObject(By.text("Steer")),5000));device.findObject(By.text("Steer")).click();idle();assertTrue(node("deliveryButton").getContentDescription().contains("Steer"));node("deliveryButton").click();assertTrue(device.wait(Until.hasObject(By.text("Queue")),5000));device.findObject(By.text("Queue")).click();idle();assertTrue(node("deliveryButton").getContentDescription().contains("Queue"));}
}

    @Test public void chatLoadingDisappearsAfterSnapshot() throws Exception {
    try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(ChatFixture::prepareLoading);idle();assertTrue(device.hasObject(By.res(context.getPackageName(),"chatLoadingSpinner")));org.json.JSONObject frame=new org.json.JSONObject().put("sessionId","ui-test-no-agent").put("status","idle").put("messages",new org.json.JSONArray());scenario.onActivity(a->a.onSnapshot(ru.billyhargrove.pimobile.core.SnapshotParser.parse(frame)));idle();assertFalse(device.hasObject(By.res(context.getPackageName(),"chatLoadingSpinner")));assertTrue(device.hasObject(By.text("No messages yet")));}
}

    @Test public void bubbleColorPreferenceAndContrast(){
        android.content.SharedPreferences prefs=context.getSharedPreferences("appearance",Context.MODE_PRIVATE);boolean had=prefs.contains("userBubble");int old=prefs.getInt("userBubble",0);
        try{prefs.edit().putInt("userBubble",android.graphics.Color.WHITE).commit();assertEquals(android.graphics.Color.WHITE,ru.billyhargrove.pimobile.ui.BubbleColors.color(context));assertEquals(android.graphics.Color.BLACK,ru.billyhargrove.pimobile.ui.BubbleColors.foreground(android.graphics.Color.WHITE));assertEquals(android.graphics.Color.WHITE,ru.billyhargrove.pimobile.ui.BubbleColors.foreground(android.graphics.Color.BLACK));}finally{if(had)prefs.edit().putInt("userBubble",old).commit();else prefs.edit().remove("userBubble").commit();}
    }

    @Test public void updaterParsesPartialApkAndSharesOnlyUpdateDirectory()throws Exception {
        java.io.File dir=new java.io.File(context.getCacheDir(),"updates");dir.mkdirs();java.io.File candidate=new java.io.File(dir,"test.part");
        try{java.nio.file.Files.copy(new java.io.File(context.getApplicationInfo().sourceDir).toPath(),candidate.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            android.content.pm.PackageInfo info=context.getPackageManager().getPackageArchiveInfo(candidate.getAbsolutePath(),android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);assertNotNull(info);assertEquals(context.getPackageName(),info.packageName);assertNotNull(info.signingInfo);assertTrue(info.signingInfo.getApkContentsSigners().length>0);
            android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(context,context.getPackageName()+".updates",candidate);try(java.io.InputStream input=context.getContentResolver().openInputStream(uri)){assertEquals('P',input.read());assertEquals('K',input.read());}
            try{androidx.core.content.FileProvider.getUriForFile(context,context.getPackageName()+".updates",new java.io.File(context.getFilesDir(),"private-token"));fail("Must not expose private app files");}catch(IllegalArgumentException expected){}
        }finally{candidate.delete();}
    }

    private float whiteThumbCenter(View slider) {
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(slider.getWidth(),slider.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);slider.draw(new android.graphics.Canvas(bitmap));int first=-1,last=-1;
        for(int x=0;x<bitmap.getWidth();x++){int color=bitmap.getPixel(x,bitmap.getHeight()/2);if(android.graphics.Color.red(color)>190&&android.graphics.Color.blue(color)>120&&android.graphics.Color.green(color)<180){if(first<0)first=x;last=x;}}
        bitmap.recycle();assertTrue("Thumb must be visible",first>=0);return (first+last)/2f;
    }

    @Test public void effortThumbActuallyTravelsThroughIntermediatePositions() throws Exception {
        org.junit.Assume.assumeTrue(ru.billyhargrove.pimobile.ui.ExpressiveMotion.enabled());
        final ru.billyhargrove.pimobile.ui.EffortSlider[] slider={null};final float[] positions=new float[2];
        final java.util.List<Float> frames=new java.util.ArrayList<>();final java.util.concurrent.CountDownLatch sampled=new java.util.concurrent.CountDownLatch(1);
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {scenario.onActivity(ChatFixture::prepareLoading);
            org.json.JSONArray levels=new org.json.JSONArray("[\"low\",\"medium\",\"high\"]");
            scenario.onActivity(a->{slider[0]=new ru.billyhargrove.pimobile.ui.EffortSlider(a);slider[0].configure(levels,"low",null);((android.view.ViewGroup)root(a)).addView(slider[0],new android.view.ViewGroup.LayoutParams(800,160));});idle();
            scenario.onActivity(a->{
                positions[0]=whiteThumbCenter(slider[0]);slider[0].setProgress(2);
                assertEquals("Selection must not teleport",positions[0],whiteThumbCenter(slider[0]),1);
                long start=android.os.SystemClock.uptimeMillis();
                android.view.Choreographer.getInstance().postFrameCallback(new android.view.Choreographer.FrameCallback(){
                    public void doFrame(long frameTimeNanos){
                        frames.add(whiteThumbCenter(slider[0]));
                        if(android.os.SystemClock.uptimeMillis()-start<600)android.view.Choreographer.getInstance().postFrameCallback(this);
                        else sampled.countDown();
                    }
                });
            });
            assertTrue("Animation frames were not sampled",sampled.await(5,java.util.concurrent.TimeUnit.SECONDS));
            scenario.onActivity(a->positions[1]=whiteThumbCenter(slider[0]));
            assertTrue(frames.toString(),frames.stream().anyMatch(x->x>positions[0]+5&&x<positions[1]-5));
        }
    }

    @Test public void settingsHaveGroupedConnectionForm() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {scenario.onActivity(a->CatalogFixture.install(a,ru.billyhargrove.pimobile.core.Catalog.empty()));
            idle();
            if (!device.hasObject(By.res(context.getPackageName(), "connectUrlInput"))) {
                device.findObject(By.res(context.getPackageName(), "settingsButton")).click();
            }
            assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(), "connectUrlInput")), 5000));
            assertTrue(device.hasObject(By.res(context.getPackageName(), "connectButton")));
            device.pressBack();
        }
    }
}
