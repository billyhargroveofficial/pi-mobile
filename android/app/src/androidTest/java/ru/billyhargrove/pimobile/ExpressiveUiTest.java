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

    @Test public void usesExpressiveMaterialComponents() {
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chat())) {
            idle();
            scenario.onActivity(a -> {
                assertTrue(a.findViewById(R.id.sendButton) instanceof MaterialButton);
                assertTrue(a.findViewById(R.id.behaviorMenuButton) instanceof MaterialButton);
                assertTrue(a.findViewById(R.id.modelButton).getParent()==a.findViewById(R.id.sendButton).getParent());
                assertNotNull(a.findViewById(R.id.sendButton).getContentDescription());
                assertNotNull(a.findViewById(R.id.attachImageButton).getContentDescription());
            });
        }
    }

    @Test public void behaviorSelectionIsExclusiveAndPersisted() {
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chat())) {
            idle();
            final ru.billyhargrove.pimobile.core.CommandBuilder.Behavior[] original={null};
            scenario.onActivity(a -> original[0]=PiApp.get(a).settings().behavior());
            try {
                device.findObject(By.res(context.getPackageName(),"behaviorMenuButton")).click();
                assertTrue(device.wait(Until.hasObject(By.text("Вмешаться в текущую задачу")),5000));
                device.findObject(By.text("Вмешаться в текущую задачу")).click();
                assertTrue(device.wait(Until.gone(By.text("Вмешаться в текущую задачу")),5000));
                idle();
                scenario.onActivity(a -> assertEquals(ru.billyhargrove.pimobile.core.CommandBuilder.Behavior.STEER,PiApp.get(a).settings().behavior()));
            } finally {scenario.onActivity(a -> PiApp.get(a).settings().setBehavior(original[0]));}
        }
    }

    @Test public void composerActionsFitAndHave48dpTouchTargets() {
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chat())) {
            idle();
            scenario.onActivity(a -> {
                Rect root = new Rect(); a.findViewById(R.id.chatRoot).getGlobalVisibleRect(root);
                for (int id : new int[]{R.id.sendButton, R.id.attachImageButton, R.id.modelButton, R.id.behaviorMenuButton}) {
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

    @Test public void toolsStayExpandedAcrossStreamingUpdates() {
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chat())) {
            final ru.billyhargrove.pimobile.ui.MessageAdapter[] adapter = new ru.billyhargrove.pimobile.ui.MessageAdapter[1];
            scenario.onActivity(a -> {
                adapter[0] = new ru.billyhargrove.pimobile.ui.MessageAdapter(PiApp.get(a).mediaLoader(), null, null);
                ((androidx.recyclerview.widget.RecyclerView)a.findViewById(R.id.messageList)).setAdapter(adapter[0]);
                adapter[0].submit(java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.ChatMessage("tool:one",ru.billyhargrove.pimobile.core.ChatMessage.Role.TOOL_RESULT,"partial-one",null,"bash",ru.billyhargrove.pimobile.core.ChatMessage.LocalState.NONE,"","running")));
            });
            idle();
            assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(), "toolToggle")),5000));
            assertFalse(device.hasObject(By.res(context.getPackageName(), "messageText")));
            device.findObject(By.res(context.getPackageName(), "toolToggle")).click();
            assertTrue(device.wait(Until.hasObject(By.text("partial-one")),5000));
            scenario.onActivity(a -> adapter[0].submit(java.util.Collections.singletonList(new ru.billyhargrove.pimobile.core.ChatMessage("tool:one",ru.billyhargrove.pimobile.core.ChatMessage.Role.TOOL_RESULT,"partial-two",null,"bash",ru.billyhargrove.pimobile.core.ChatMessage.LocalState.NONE,"","running"))));
            assertTrue(device.wait(Until.hasObject(By.text("partial-two")),5000));
            device.findObject(By.res(context.getPackageName(), "toolToggle")).click();
            assertTrue(device.wait(Until.gone(By.res(context.getPackageName(), "messageText")),5000));
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
