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
import org.junit.Test;
import org.junit.runner.RunWith;

/** UI-only checks: never send a prompt or stop any real Pi session. */
@RunWith(AndroidJUnit4.class)
public class ExpressiveUiTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private Intent chat() { return ChatActivity.intent(context, "ui-test-no-agent", "Проверка дизайна", false); }
    private void idle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); device.waitForIdle(); }

    @Test public void catalogHasSeparateHistoryAction() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            idle();
            scenario.onActivity(a -> {
                View button = a.findViewById(R.id.historyButton);
                assertTrue(button instanceof MaterialButton);
                Rect root = new Rect(), bounds = new Rect();
                a.findViewById(R.id.mainRoot).getGlobalVisibleRect(root);
                assertTrue(button.getGlobalVisibleRect(bounds));
                assertTrue(root.contains(bounds));
                assertTrue(button.getHeight() >= 48 * a.getResources().getDisplayMetrics().density - 1);
                assertEquals("История", ((MaterialButton)button).getText().toString());
            });
        }
    }

    @Test public void usesExpressiveMaterialComponents() {
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chat())) {
            idle();
            scenario.onActivity(a -> {
                assertTrue(a.findViewById(R.id.sendButton) instanceof MaterialButton);
                assertTrue(a.findViewById(R.id.effortButton) instanceof MaterialButton);
                assertTrue(a.findViewById(R.id.effortButton).getParent()==a.findViewById(R.id.sendButton).getParent());
                assertNotNull(a.findViewById(R.id.sendButton).getContentDescription());
                assertNotNull(a.findViewById(R.id.attachImageButton).getContentDescription());
            });
        }
    }

    @Test public void composerHasFourEqualCircularIcons() {
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chat())) {
            idle();scenario.onActivity(a -> {
                android.view.ViewGroup row=(android.view.ViewGroup)a.findViewById(R.id.sendButton).getParent();int count=0;
                for(int i=0;i<row.getChildCount();i++)if(row.getChildAt(i) instanceof MaterialButton){MaterialButton b=(MaterialButton)row.getChildAt(i);count++;assertEquals("",b.getText().toString());assertCircle(b);assertEquals(a.findViewById(R.id.sendButton).getWidth(),b.getWidth());}
                assertEquals(4,count);assertCircle(a.findViewById(R.id.backButton));
            });
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {idle();scenario.onActivity(a->{assertCircle(a.findViewById(R.id.settingsButton));assertCircle(a.findViewById(R.id.refreshButton));});}
    }

    private void assertCircle(MaterialButton button) {
        assertFalse("Button shape must not morph",button.getShapeAppearance().isStateful());
        assertEquals(button.getWidth(),button.getHeight());
        float corner=button.getShapeAppearanceModel().getTopLeftCornerSize().getCornerSize(new android.graphics.RectF(0,0,button.getWidth(),button.getHeight()));
        assertEquals(button.getWidth()/2f,corner,1f);
        assertEquals(MaterialButton.ICON_GRAVITY_TEXT_START,button.getIconGravity());
        assertEquals("Icon must be centered inside its circle",button.getWidth()/2f,button.getPaddingLeft()+button.getIcon().getBounds().exactCenterX(),1f);
    }

    @Test public void compactComposerAndWorkingBadge() throws Exception {
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chat())) {
            idle();
            org.json.JSONObject frame=new org.json.JSONObject().put("sessionId","ui-test-no-agent").put("activeTurnId","turn").put("turns",new org.json.JSONArray().put(new org.json.JSONObject().put("id","turn").put("startedAt",System.currentTimeMillis()-1588000)));
            scenario.onActivity(a -> {
                assertEquals(48*a.getResources().getDisplayMetrics().density,a.findViewById(R.id.composerEditor).getHeight(),1);
                assertNull(a.findViewById(R.id.historyProgress));
                a.onTimelineMeta(frame);
                String label=((android.widget.TextView)a.findViewById(R.id.workingBadge)).getText().toString();
                assertTrue(label,label.startsWith("Working · 26м"));
            });
        }
    }

    @Test public void composerActionsFitAndHave48dpTouchTargets() {
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chat())) {
            idle();
            scenario.onActivity(a -> {
                Rect root = new Rect(); a.findViewById(R.id.chatRoot).getGlobalVisibleRect(root);
                for (int id : new int[]{R.id.sendButton, R.id.attachImageButton, R.id.effortButton}) {
                    View view = a.findViewById(id); Rect rect = new Rect();
                    assertTrue(view.getGlobalVisibleRect(rect));
                    assertTrue(root.contains(rect));
                    assertTrue(view.getHeight() >= 48 * a.getResources().getDisplayMetrics().density - 1);
                    assertEquals(view.getWidth(), rect.width());
                }
            });
        }
    }

    @Test public void keyboardDoesNotCoverComposer() {
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chat())) {
            idle();
            assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(), "composerInput")), 5000));
            device.findObject(By.res(context.getPackageName(), "composerInput")).click();
            scenario.onActivity(a -> {
                EditText input=a.findViewById(R.id.composerInput);input.requestFocus();
                ((android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
            java.util.concurrent.atomic.AtomicBoolean shown=new java.util.concurrent.atomic.AtomicBoolean();
            for(int attempt=0;attempt<50&&!shown.get();attempt++){
                scenario.onActivity(a->{androidx.core.view.WindowInsetsCompat i=androidx.core.view.ViewCompat.getRootWindowInsets(a.findViewById(R.id.chatRoot));shown.set(i!=null&&i.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));});
                if(!shown.get())android.os.SystemClock.sleep(100);
            }
            device.waitForIdle();
            scenario.onActivity(a -> {
                View root = a.findViewById(R.id.chatRoot);
                androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(root);
                assertNotNull(insets);
                assertTrue("IME must actually be shown", insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));
                int keyboardTop = root.getHeight() - insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
                View send = a.findViewById(R.id.sendButton); int[] xy = new int[2]; send.getLocationOnScreen(xy);
                assertTrue("Send must remain above IME", xy[1] + send.getHeight() <= keyboardTop);
            });
            device.pressBack();
        }
    }

    private java.util.List<ru.billyhargrove.pimobile.core.ChatMessage> toolMessages(int count) {
        java.util.List<ru.billyhargrove.pimobile.core.ChatMessage> rows=new java.util.ArrayList<>();
        for(int i=0;i<count;i++)rows.add(new ru.billyhargrove.pimobile.core.ChatMessage("tool:"+i,ru.billyhargrove.pimobile.core.ChatMessage.Role.TOOL_RESULT,"command "+i,null,"bash",ru.billyhargrove.pimobile.core.ChatMessage.LocalState.NONE,"","done"));
        return rows;
    }

    @Test public void progressSeparatesToolsAndAllSegmentsCollapseOnFinish()throws Exception {
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {
            final ru.billyhargrove.pimobile.ui.MessageAdapter[] adapter={null};java.util.List<ru.billyhargrove.pimobile.core.ChatMessage> source=new java.util.ArrayList<>();source.add(toolMessages(1).get(0).withPresentation("turn","work","", ""));source.add(ru.billyhargrove.pimobile.core.ChatMessage.remote("progress",ru.billyhargrove.pimobile.core.ChatMessage.Role.ASSISTANT,"Проверяю исправление",null,null).withPresentation("turn","work","",""));source.add(toolMessages(2).get(1).withPresentation("turn","work","",""));
            org.json.JSONObject active=new org.json.JSONObject().put("activeTurnId","turn");scenario.onActivity(a->{adapter[0]=new ru.billyhargrove.pimobile.ui.MessageAdapter(PiApp.get(a).mediaLoader(),null,null);((androidx.recyclerview.widget.RecyclerView)a.findViewById(R.id.messageList)).setAdapter(adapter[0]);adapter[0].metadata(active);adapter[0].submit(source);});idle();assertEquals(2,device.findObjects(By.res(context.getPackageName(),"workLogList")).size());assertTrue(device.hasObject(By.text("Проверяю исправление")));device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"progress-active.png"));
            org.json.JSONObject done=new org.json.JSONObject().put("activeTurnId","").put("turns",new org.json.JSONArray().put(new org.json.JSONObject().put("id","turn").put("finishedAt",123)));scenario.onActivity(a->adapter[0].metadata(done));idle();assertFalse(device.hasObject(By.res(context.getPackageName(),"workLogList")));assertTrue(device.hasObject(By.desc("Развернуть сообщение о ходе работы")));device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"progress-settled.png"));device.findObjects(By.desc("Развернуть действия")).get(0).click();idle();assertEquals(1,device.findObjects(By.res(context.getPackageName(),"workLogList")).size());scenario.onActivity(a->adapter[0].metadata(done));idle();assertEquals("Repeated metadata must preserve manual expansion",1,device.findObjects(By.res(context.getPackageName(),"workLogList")).size());scenario.onActivity(a->adapter[0].close());
        }
    }

    @Test public void longAnswerStaysAtBottomAndUpdatesDoNotPullReaderUp()throws Exception {
        org.json.JSONArray messages=new org.json.JSONArray();messages.put(new org.json.JSONObject().put("id","u").put("role","user").put("text","Вопрос").put("turnId","turn"));messages.put(new org.json.JSONObject().put("id","a").put("role","assistant").put("text",String.join("\n",java.util.Collections.nCopies(140,"Строка ответа"))).put("turnId","turn"));org.json.JSONObject data=new org.json.JSONObject().put("sessionId","ui-test-no-agent").put("messages",messages).put("status","idle");
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){scenario.onActivity(a->a.onSnapshot(ru.billyhargrove.pimobile.core.SnapshotParser.parse(data)));idle();scenario.onActivity(a->assertFalse("Long final answer must align its END, not its top",a.findViewById(R.id.messageList).canScrollVertically(1)));
            Rect bounds=device.findObject(By.res(context.getPackageName(),"messageList")).getVisibleBounds();device.swipe(bounds.centerX(),bounds.top+200,bounds.centerX(),bounds.bottom-200,25);idle();final int[] top={0};scenario.onActivity(a->{androidx.recyclerview.widget.RecyclerView list=a.findViewById(R.id.messageList);assertTrue(list.canScrollVertically(1));top[0]=list.getChildAt(0).getTop();a.onSnapshot(ru.billyhargrove.pimobile.core.SnapshotParser.parse(data));});idle();scenario.onActivity(a->{androidx.recyclerview.widget.RecyclerView list=a.findViewById(R.id.messageList);assertEquals(top[0],list.getChildAt(0).getTop());});}
    }

    @Test public void pendingReceiptsSurviveReentryWithoutAutomaticReplay(){ru.billyhargrove.pimobile.store.PendingMessages store=new ru.billyhargrove.pimobile.store.PendingMessages(context,"mock-host","mock-outbox");try{store.save(java.util.Collections.singletonList(ru.billyhargrove.pimobile.core.ChatMessage.local("r","Сохранённый вопрос",null,ru.billyhargrove.pimobile.core.ChatMessage.LocalState.SENDING)));ru.billyhargrove.pimobile.store.PendingMessages reopened=new ru.billyhargrove.pimobile.store.PendingMessages(context,"mock-host","mock-outbox");assertEquals("Сохранённый вопрос",reopened.load().get(0).text());assertEquals(ru.billyhargrove.pimobile.core.ChatMessage.LocalState.UNCERTAIN,reopened.load().get(0).localState());}finally{store.save(java.util.Collections.emptyList());}}

    @Test public void workBubbleFitsSmallLogsAndCapsLargeLogs() {
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {
            final ru.billyhargrove.pimobile.ui.MessageAdapter[] adapter={null};
            scenario.onActivity(a->{adapter[0]=new ru.billyhargrove.pimobile.ui.MessageAdapter(PiApp.get(a).mediaLoader(),null,null);((androidx.recyclerview.widget.RecyclerView)a.findViewById(R.id.messageList)).setAdapter(adapter[0]);adapter[0].submit(toolMessages(2));});
            idle();
            scenario.onActivity(a->{View log=a.findViewById(R.id.workLogList);float density=a.getResources().getDisplayMetrics().density;assertNotNull(log);assertTrue("Two tools must not reserve the whole 200dp",log.getHeight()<=50*density);assertTrue(log.getHeight()>=40*density-1);adapter[0].submit(toolMessages(40));});
            idle();
            scenario.onActivity(a->{View log=a.findViewById(R.id.workLogList);assertEquals(200*a.getResources().getDisplayMetrics().density,log.getHeight(),1);assertTrue("Overflow must scroll inside the bubble",log.canScrollVertically(-1)||log.canScrollVertically(1));adapter[0].submit(toolMessages(1));});
            idle();
            scenario.onActivity(a->{View log=a.findViewById(R.id.workLogList);assertTrue("Recycled long log must shrink back",log.getHeight()<=30*a.getResources().getDisplayMetrics().density);adapter[0].close();});
        }
    }

    @Test public void workLogRespondsToFingerScrollAndHeaderTap() {
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){
            final ru.billyhargrove.pimobile.ui.MessageAdapter[] adapter={null};final int[] first=new int[2];
            scenario.onActivity(a->{adapter[0]=new ru.billyhargrove.pimobile.ui.MessageAdapter(PiApp.get(a).mediaLoader(),null,null);((androidx.recyclerview.widget.RecyclerView)a.findViewById(R.id.messageList)).setAdapter(adapter[0]);adapter[0].submit(toolMessages(40));});idle();
            scenario.onActivity(a->{androidx.recyclerview.widget.RecyclerView log=a.findViewById(R.id.workLogList);first[0]=((androidx.recyclerview.widget.LinearLayoutManager)log.getLayoutManager()).findFirstVisibleItemPosition();});
            Rect bounds=device.findObject(By.res(context.getPackageName(),"workLogList")).getVisibleBounds();device.swipe(bounds.centerX(),bounds.top+50,bounds.centerX(),bounds.bottom-50,35);idle();
            scenario.onActivity(a->{androidx.recyclerview.widget.RecyclerView log=a.findViewById(R.id.workLogList);first[1]=((androidx.recyclerview.widget.LinearLayoutManager)log.getLayoutManager()).findFirstVisibleItemPosition();});assertTrue(java.util.Arrays.toString(first),first[1]<first[0]);
            device.findObject(By.res(context.getPackageName(),"workHeader")).click();idle();assertFalse(device.hasObject(By.res(context.getPackageName(),"workLogList")));device.findObject(By.res(context.getPackageName(),"workHeader")).click();idle();assertTrue(device.hasObject(By.res(context.getPackageName(),"workLogList")));scenario.onActivity(a->adapter[0].close());
        }
    }

    @Test public void effortPopupRespondsToFingerDrag() throws Exception {
        org.json.JSONObject model=new org.json.JSONObject("{\"thinkingLevels\":[\"low\",\"medium\",\"high\"]}");final String[] sent={null};
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){
            idle();scenario.onActivity(a->new ru.billyhargrove.pimobile.ui.EffortPopup(a.findViewById(R.id.effortButton),model,"low",value->sent[0]=value));assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"quickEffortSlider")),5000));idle();
            Rect r=device.findObject(By.res(context.getPackageName(),"quickEffortSlider")).getVisibleBounds();device.swipe(r.left+70,r.centerY(),r.right-70,r.centerY(),35);idle();assertEquals("high",sent[0]);
        }
    }

    @Test public void toolRowsStayDenseAndNeverExpand() {
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chat())) {
            final ru.billyhargrove.pimobile.ui.MessageAdapter[] adapter = new ru.billyhargrove.pimobile.ui.MessageAdapter[1];
            scenario.onActivity(a -> {
                adapter[0] = new ru.billyhargrove.pimobile.ui.MessageAdapter(PiApp.get(a).mediaLoader(), null, null);
                ((androidx.recyclerview.widget.RecyclerView)a.findViewById(R.id.messageList)).setAdapter(adapter[0]);
                adapter[0].submit(java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.ChatMessage("tool:one",ru.billyhargrove.pimobile.core.ChatMessage.Role.TOOL_RESULT,"partial-one",null,"bash",ru.billyhargrove.pimobile.core.ChatMessage.LocalState.NONE,"","running")));
            });
            idle();
            assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(), "toolLine")),5000));
            scenario.onActivity(a -> {
                View line = a.findViewById(R.id.toolLine);
                assertFalse(line.isClickable()); assertFalse(line.isLongClickable()); assertFalse(line.isFocusable());
                assertTrue("Tool row must remain at most 22dp", line.getHeight() <= 22 * a.getResources().getDisplayMetrics().density + 1);
                assertFalse(line.performClick()); assertFalse(line.performLongClick());
            });
            assertFalse(device.hasObject(By.res(context.getPackageName(), "messageText")));
            device.findObject(By.res(context.getPackageName(), "toolLine")).click();
            assertFalse(device.hasObject(By.res(context.getPackageName(), "messageText")));
            scenario.onActivity(a -> adapter[0].submit(java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.ChatMessage("tool:one",ru.billyhargrove.pimobile.core.ChatMessage.Role.TOOL_RESULT,"partial-two",null,"bash",ru.billyhargrove.pimobile.core.ChatMessage.LocalState.NONE,"","running"))));
            assertTrue(device.wait(Until.hasObject(By.text("partial-two")),5000));
            device.findObject(By.res(context.getPackageName(), "toolLine")).click();
            assertFalse(device.hasObject(By.res(context.getPackageName(), "messageText")));
            scenario.onActivity(a -> adapter[0].close());
        }
    }

    @Test public void imageOpensFullscreenAndCanBeZoomed() {
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chat())) {
            scenario.onActivity(a -> {android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(80,80,android.graphics.Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.GREEN);ru.billyhargrove.pimobile.ui.ImageViewer.show(a,"local:test",b);});
            assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"zoomImage")),5000));
            androidx.test.uiautomator.UiObject2 image=device.findObject(By.res(context.getPackageName(),"zoomImage"));
            image.pinchOpen(0.5f);image.pinchClose(0.5f);
            device.findObject(By.res(context.getPackageName(),"closeImageButton")).click();
            assertTrue(device.wait(Until.gone(By.res(context.getPackageName(),"zoomImage")),5000));
        }
    }

    @Test public void speedometerModelRowOpensNextPickerWithoutApplying() throws Exception {
        org.json.JSONObject model=new org.json.JSONObject("{\"id\":\"a\",\"name\":\"Model A\",\"thinkingLevels\":[\"low\",\"high\"]}");final boolean[] opened={false};final String[] sent={null};
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {idle();scenario.onActivity(a->new ru.billyhargrove.pimobile.ui.EffortPopup(a.findViewById(R.id.effortButton),model,"low",true,()->opened[0]=true,value->sent[0]=value));assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"popupModelButton")),5000));device.findObject(By.res(context.getPackageName(),"popupModelButton")).click();idle();assertTrue(opened[0]);assertNull(sent[0]);}
    }

    @Test public void paletteUsesAndroidSystemColors() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(a->{if(android.os.Build.VERSION.SDK_INT>=31){boolean dark=(a.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;assertEquals(a.getColor(dark?android.R.color.system_accent1_200:android.R.color.system_accent1_600),a.getColor(R.color.accent));assertEquals(a.getColor(dark?android.R.color.system_neutral1_900:android.R.color.system_neutral1_10),a.getColor(R.color.bg));}});}
    }

    @Test public void modelListChangesEffortRangeAndWaitsForDone() throws Exception {
        org.json.JSONObject config=new org.json.JSONObject("{\"model\":\"test/a\",\"thinkingLevel\":\"high\",\"models\":[{\"provider\":\"test\",\"id\":\"a\",\"name\":\"Model A\",\"thinkingLevels\":[\"off\",\"high\"]},{\"provider\":\"test\",\"id\":\"b\",\"name\":\"Model B\",\"thinkingLevels\":[\"medium\",\"xhigh\"]}]}");
        final String[] sent={null};final ru.billyhargrove.pimobile.ui.ModelSettingsSheet[] sheet={null};
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {
            scenario.onActivity(a->{sheet[0]=new ru.billyhargrove.pimobile.ui.ModelSettingsSheet(a,config,true,(p,m,e)->sent[0]=p+"/"+m+":"+e);sheet[0].show();});
            assertTrue(device.wait(Until.hasObject(By.text("Model B")),5000));device.findObject(By.text("Model B")).click();idle();
            scenario.onActivity(a->{assertTrue(sheet[0].findViewById(R.id.modelSelector) instanceof androidx.recyclerview.widget.RecyclerView);ru.billyhargrove.pimobile.ui.EffortSlider slider=sheet[0].findViewById(R.id.effortSelector);assertEquals("medium",slider.value());assertEquals(1,slider.getMax());slider.setProgress(1);});
            assertNull(sent[0]);device.findObject(By.res(context.getPackageName(),"applyModelButton")).click();idle();assertEquals("test/b:xhigh",sent[0]);scenario.onActivity(a->sheet[0].dismiss());
        }
    }

    @Test public void quickEffortKeepsKeyboardAndCommitsOnRelease() throws Exception {
        org.json.JSONObject model=new org.json.JSONObject("{\"thinkingLevels\":[\"low\",\"medium\",\"high\"]}");
        final String[] sent={null};final ru.billyhargrove.pimobile.ui.EffortPopup[] popup={null};
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {
            idle();scenario.onActivity(a->{EditText input=a.findViewById(R.id.composerInput);input.requestFocus();((android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
            java.util.concurrent.atomic.AtomicBoolean shown=new java.util.concurrent.atomic.AtomicBoolean();
            for(int i=0;i<50&&!shown.get();i++){scenario.onActivity(a->{androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(a.findViewById(R.id.chatRoot));shown.set(insets!=null&&insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));});if(!shown.get())android.os.SystemClock.sleep(100);}
            assertTrue(shown.get());scenario.onActivity(a->popup[0]=new ru.billyhargrove.pimobile.ui.EffortPopup(a.findViewById(R.id.effortButton),model,"low",value->sent[0]=value));
            assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"quickEffortSlider")),5000));idle();assertNull(sent[0]);
            scenario.onActivity(a->assertTrue(androidx.core.view.ViewCompat.getRootWindowInsets(a.findViewById(R.id.chatRoot)).isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())));
            Rect slider=device.findObject(By.res(context.getPackageName(),"quickEffortSlider")).getVisibleBounds();device.click(slider.right-30,slider.centerY());idle();assertEquals("high",sent[0]);assertFalse(popup[0].isShowing());device.pressBack();
        }
    }

    @Test public void modelSheetSendsSelectedModelAndEffortOnlyOnApply() throws Exception {
        org.json.JSONObject config=new org.json.JSONObject("{\"model\":\"test/a\",\"thinkingLevel\":\"high\",\"models\":[{\"provider\":\"test\",\"id\":\"a\",\"thinkingLevels\":[\"off\",\"high\"]}]}");
        final String[] sent={null};
        final ru.billyhargrove.pimobile.ui.ModelSettingsSheet[] sheet=new ru.billyhargrove.pimobile.ui.ModelSettingsSheet[1];
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {
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
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {
            scenario.onActivity(a->{preview[0]=new ru.billyhargrove.pimobile.ui.MarkdownPreview(a,"example.md","## Заголовок\n\n**Жирный** и `code`.\n\n$$\n\\frac{a}{b}=x^2\n$$\n",link->{});preview[0].show();});
            for(int attempt=0;attempt<80&&!ready.get();attempt++){
                scenario.onActivity(a->{android.widget.TextView text=preview[0].findViewById(R.id.documentText);if(text!=null&&text.getText() instanceof android.text.Spanned){android.text.Spanned s=(android.text.Spanned)text.getText();io.noties.markwon.image.AsyncDrawableSpan[] spans=s.getSpans(0,s.length(),io.noties.markwon.image.AsyncDrawableSpan.class);ready.set(spans.length>0&&spans[0].getDrawable().hasResult());}});
                if(!ready.get())android.os.SystemClock.sleep(100);
            }
            assertTrue("LaTeX must produce an actual drawable, not raw source",ready.get());
            scenario.onActivity(a->{android.widget.TextView text=preview[0].findViewById(R.id.documentText);assertFalse(text.getText().toString().contains("**Жирный**"));preview[0].dismiss();});
        }
    }

    @Test public void archiveStartsWithSkeletonsAndNativeSearch() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{ru.billyhargrove.pimobile.ui.ArchiveSheet sheet=new ru.billyhargrove.pimobile.ui.ArchiveSheet(a,PiApp.get(a),(id,title)->fail("Loading must not open a session"));sheet.show();androidx.recyclerview.widget.RecyclerView list=sheet.findViewById(R.id.archiveList);assertNotNull(list);assertEquals(7,list.getAdapter().getItemCount());assertEquals(3,list.getAdapter().getItemViewType(0));assertEquals(2,list.getAdapter().getItemViewType(1));EditText search=sheet.findViewById(R.id.archiveSearch);assertNull("Search must have no legacy underline",search.getBackground());sheet.dismiss();});
        }
    }

    @Test public void archiveSkeletonHasExactlyTheLoadedRowGeometry() throws Exception {
        java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);final ru.billyhargrove.pimobile.ui.ArchiveSheet[] sheet={null};final Rect[] before={new Rect(),new Rect()},after={new Rect(),new Rect()};
        org.json.JSONArray rows=new org.json.JSONArray();for(int i=0;i<6;i++)rows.put(new org.json.JSONObject().put("id","mock-"+i).put("title","Диалог "+i).put("workspaceName","Workspace").put("messageCount",12).put("modified",java.time.Instant.now().toString()));org.json.JSONObject data=new org.json.JSONObject().put("sessions",rows).put("hasMore",false);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{sheet[0]=new ru.billyhargrove.pimobile.ui.ArchiveSheet(a,(offset,query)->{if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("test timeout");return data;},(id,title)->{});sheet[0].show();});idle();
            scenario.onActivity(a->{androidx.recyclerview.widget.RecyclerView list=sheet[0].findViewById(R.id.archiveList);for(int i=0;i<2;i++)assertTrue(list.getLayoutManager().findViewByPosition(i).getGlobalVisibleRect(before[i]));});release.countDown();assertTrue(device.wait(Until.hasObject(By.text("Диалог 0")),5000));idle();
            scenario.onActivity(a->{androidx.recyclerview.widget.RecyclerView list=sheet[0].findViewById(R.id.archiveList);for(int i=0;i<2;i++){assertTrue(list.getLayoutManager().findViewByPosition(i).getGlobalVisibleRect(after[i]));assertEquals("Header/card must not jump after loading",before[i],after[i]);}sheet[0].dismiss();});
        }finally{release.countDown();}
    }

    @Test public void emptyWorkspacePlusRequiresConfirmation() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            idle();
            scenario.onActivity(a->{a.onConnectionState(ru.billyhargrove.pimobile.core.ConnectionState.CONNECTED,"");a.onCatalog(new ru.billyhargrove.pimobile.core.Catalog(java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.Workspace("mock-workspace","Пустое пространство","/mock")),null,null));});idle();
            java.util.concurrent.atomic.AtomicBoolean settled=new java.util.concurrent.atomic.AtomicBoolean();for(int i=0;i<80&&!settled.get();i++){scenario.onActivity(a->{View list=a.findViewById(R.id.catalogList);settled.set(list.getAlpha()==1f&&list.getTranslationY()==0f);});if(!settled.get())android.os.SystemClock.sleep(20);}assertTrue("Catalog entrance must settle",settled.get());
            assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"newSessionButton")),5000));device.findObject(By.res(context.getPackageName(),"newSessionButton")).click();
            assertTrue(device.wait(Until.hasObject(By.text("Новая сессия")),5000));assertTrue(device.hasObject(By.textContains("Пустое пространство")));device.findObject(By.text("Отмена")).click();
        }
    }

    @Test public void swipeLiveSessionWarnsBeforeInterrupting() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            idle();
            scenario.onActivity(a->{a.onConnectionState(ru.billyhargrove.pimobile.core.ConnectionState.CONNECTED,"");a.onCatalog(new ru.billyhargrove.pimobile.core.Catalog(java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.Workspace("mock-workspace","Тест","/mock")),java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.Session("mock-session","Тестовая задача","/mock","mock-workspace","mock-terminal",true,ru.billyhargrove.pimobile.core.SessionStatus.RUNNING,"mock/model")),null));});idle();
            assertTrue(device.wait(Until.hasObject(By.text("Тестовая задача")),5000));Rect bounds=device.findObject(By.text("Тестовая задача")).getVisibleBounds();device.swipe(device.getDisplayWidth()-80,bounds.centerY(),60,bounds.centerY(),18);
            assertTrue(device.wait(Until.hasObject(By.text("Прервать Pi и закрыть вкладку?")),5000));assertTrue(device.hasObject(By.textContains("История останется на диске")));device.findObject(By.text("Отмена")).click();
        }
    }

    @Test public void swipeArchiveDeletesOnlyAfterConfirmation() throws Exception {
        final java.util.concurrent.atomic.AtomicInteger deletes=new java.util.concurrent.atomic.AtomicInteger();final ru.billyhargrove.pimobile.ui.ArchiveSheet[] sheet={null};org.json.JSONObject data=new org.json.JSONObject().put("sessions",new org.json.JSONArray().put(new org.json.JSONObject().put("id","mock-delete").put("title","Удаляемый диалог").put("modified",java.time.Instant.now().toString())));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{sheet[0]=new ru.billyhargrove.pimobile.ui.ArchiveSheet(a,(offset,query)->data,(id,title)->fail("Swipe must not resume"),(id,success,failure)->{assertEquals("mock-delete",id);deletes.incrementAndGet();});sheet[0].show();});
            assertTrue(device.wait(Until.hasObject(By.text("Удаляемый диалог")),5000));idle();Rect bounds=device.findObject(By.text("Удаляемый диалог")).getVisibleBounds();device.swipe(device.getDisplayWidth()-80,bounds.centerY(),60,bounds.centerY(),18);
            assertTrue(device.wait(Until.hasObject(By.text("Удалить сессию с диска?")),5000));assertEquals(0,deletes.get());device.findObject(By.text("Удалить")).click();idle();assertEquals(1,deletes.get());scenario.onActivity(a->sheet[0].dismiss());
        }
    }

    @Test public void deliveryPickerChangesModeWithoutSendingPrompt() {
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())){
            idle();device.findObject(By.res(context.getPackageName(),"deliveryButton")).click();assertTrue(device.wait(Until.hasObject(By.text("Steer · вмешаться")),5000));device.findObject(By.text("Steer · вмешаться")).click();idle();
            scenario.onActivity(a->assertTrue(a.findViewById(R.id.deliveryButton).getContentDescription().toString().contains("Steer")));
            device.findObject(By.res(context.getPackageName(),"deliveryButton")).click();assertTrue(device.wait(Until.hasObject(By.text("Queue · в очередь")),5000));device.findObject(By.text("Queue · в очередь")).click();idle();
            scenario.onActivity(a->assertTrue(a.findViewById(R.id.deliveryButton).getContentDescription().toString().contains("Queue")));
        }
    }

    private float whiteThumbCenter(View slider) {
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(slider.getWidth(),slider.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);slider.draw(new android.graphics.Canvas(bitmap));int first=-1,last=-1;
        for(int x=0;x<bitmap.getWidth();x++){int color=bitmap.getPixel(x,bitmap.getHeight()/2);if(android.graphics.Color.red(color)>248&&android.graphics.Color.green(color)>248&&android.graphics.Color.blue(color)>248){if(first<0)first=x;last=x;}}
        bitmap.recycle();assertTrue("Thumb must be visible",first>=0);return (first+last)/2f;
    }

    @Test public void effortThumbActuallyTravelsThroughIntermediatePositions() throws Exception {
        org.junit.Assume.assumeTrue(ru.billyhargrove.pimobile.ui.ExpressiveMotion.enabled());
        final ru.billyhargrove.pimobile.ui.EffortSlider[] slider={null};final float[] positions=new float[3];
        try(ActivityScenario<ChatActivity> scenario=ActivityScenario.launch(chat())) {
            org.json.JSONArray levels=new org.json.JSONArray("[\"low\",\"medium\",\"high\"]");
            scenario.onActivity(a->{slider[0]=new ru.billyhargrove.pimobile.ui.EffortSlider(a);slider[0].configure(levels,"low",null);((android.view.ViewGroup)a.findViewById(R.id.chatRoot)).addView(slider[0],new android.view.ViewGroup.LayoutParams(800,160));});idle();
            scenario.onActivity(a->{positions[0]=whiteThumbCenter(slider[0]);slider[0].setProgress(2);assertEquals("Selection must not teleport",positions[0],whiteThumbCenter(slider[0]),1);});
            android.os.SystemClock.sleep(90);scenario.onActivity(a->positions[1]=whiteThumbCenter(slider[0]));android.os.SystemClock.sleep(500);scenario.onActivity(a->positions[2]=whiteThumbCenter(slider[0]));
            assertTrue(java.util.Arrays.toString(positions),positions[1]>positions[0]+5);assertTrue(java.util.Arrays.toString(positions),positions[1]<positions[2]-5);
        }
    }

    @Test public void settingsOpenAsMaterialBottomSheet() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
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
