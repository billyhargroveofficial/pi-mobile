package ru.billyhargrove.pimobile;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import ru.billyhargrove.pimobile.core.Ack;
import ru.billyhargrove.pimobile.core.ChatMessage;
import ru.billyhargrove.pimobile.core.CommandBuilder;
import ru.billyhargrove.pimobile.core.ImagePayload;
import ru.billyhargrove.pimobile.features.chat.ChatOutbox;
import ru.billyhargrove.pimobile.media.Attachment;
import ru.billyhargrove.pimobile.store.PendingMessages;

/** Synthetic Activity integration. The injected sender cannot reach a real Pi. */
@RunWith(AndroidJUnit4.class)
public class ChatOutboxUiTest {
    private static final String SESSION="chat-outbox-fixture";
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    @Before public void isolate() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->PiApp.get(context).client().disconnect());
        clearReceipts();
    }
    @After public void clearReceipts() {
        new PendingMessages(context,PiApp.get(context).settings().baseUrl(),SESSION).save(Collections.emptyList());
    }
    private ActivityScenario<ChatActivity> chat(boolean readOnly) {
        return ActivityScenario.launch(ChatActivity.intent(context,SESSION,"Outbox preview",readOnly));
    }
    private void idle() {InstrumentationRegistry.getInstrumentation().waitForIdleSync();device.waitForIdle();}
    private static void set(ChatActivity activity,String name,Object value) {
        try {Field field=ChatActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    @SuppressWarnings("unchecked") private static List<Attachment> attachments(ChatActivity activity) {
        try {Field field=ChatActivity.class.getDeclaredField("attachments");field.setAccessible(true);return (List<Attachment>)field.get(activity);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void call(ChatActivity activity,String name) {
        try {Method method=ChatActivity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(activity);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private Attachment file(String name) {return new Attachment(ImagePayload.file(new byte[]{1,2},name),null,name);}

    @Test public void failedWriteKeepsComposerAndAttachment() {
        AtomicInteger calls=new AtomicInteger();
        ChatOutbox outbox=new ChatOutbox(SESSION,false,(id,text,payloads,behavior)->{calls.incrementAndGet();return null;},Collections.emptyList());
        try(ActivityScenario<ChatActivity> scenario=chat(false)) {
            scenario.onActivity(a->{
                set(a,"outbox",outbox);EditText input=a.findViewById(R.id.composerInput);input.setText("Current draft");
                Attachment attachment=file("current.md");attachments(a).add(attachment);call(a,"renderAttachments");
                a.findViewById(R.id.sendButton).setEnabled(true);a.findViewById(R.id.sendButton).performClick();
                assertEquals("Current draft",input.getText().toString());assertSame(attachment,attachments(a).get(0));
                assertTrue(outbox.localReceipts().isEmpty());assertEquals(1,calls.get());
            });
        }
    }

    @Test public void restoreTapPreservesOccupiedComposerThenRestoresOriginalBytes() {
        Bitmap thumb=Bitmap.createBitmap(8,16,Bitmap.Config.ARGB_8888);thumb.eraseColor(0xff88aabb);
        Attachment image=new Attachment(new ImagePayload(new byte[]{3,4},"image/png"),thumb,"picture.png");
        AtomicInteger calls=new AtomicInteger();
        ChatOutbox outbox=new ChatOutbox(SESSION,false,(id,text,payloads,behavior)->"request-"+calls.incrementAndGet(),Collections.emptyList());
        String request=outbox.send("Original prompt",Arrays.asList(file("original.md"),image),CommandBuilder.Behavior.STEER).getRequestId();
        outbox.uncertain(request,SESSION);
        try(ActivityScenario<ChatActivity> scenario=chat(false)) {
            scenario.onActivity(a->{
                set(a,"outbox",outbox);attachments(a).add(file("current.md"));call(a,"renderAttachments");call(a,"render");
                assertSame(thumb,a.thumbnail(request,1));assertNull(a.thumbnail(request,0));
            });idle();
            assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(),"messageRestoreButton")),5000));
            device.findObject(By.res(context.getPackageName(),"messageRestoreButton")).click();idle();
            scenario.onActivity(a->{
                assertEquals("",((EditText)a.findViewById(R.id.composerInput)).getText().toString());
                assertEquals("current.md",attachments(a).get(0).payload().fileName());
                assertEquals(1,outbox.localReceipts().size());
                ((EditText)a.findViewById(R.id.composerInput)).setText("Current text");
                a.onRestore(outbox.localReceipts().get(0));
                assertEquals("Current text",((EditText)a.findViewById(R.id.composerInput)).getText().toString());
                ((EditText)a.findViewById(R.id.composerInput)).setText("");attachments(a).clear();call(a,"renderAttachments");
            });idle();
            device.findObject(By.res(context.getPackageName(),"messageRestoreButton")).click();idle();
            scenario.onActivity(a->{
                assertEquals("Original prompt",((EditText)a.findViewById(R.id.composerInput)).getText().toString());
                assertEquals(2,attachments(a).size());assertEquals("original.md",attachments(a).get(0).payload().fileName());
                assertSame(thumb,attachments(a).get(1).thumbnail());assertTrue(outbox.localReceipts().isEmpty());
                assertEquals(1,calls.get());
            });
            device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null),"chat-restored-draft.png"));
        } finally {thumb.recycle();}
    }

    @Test public void rejectionDoesNotMixOldTextWithNewAttachmentsAndForeignAckIsIgnored() {
        ChatOutbox outbox=new ChatOutbox(SESSION,false,(id,text,payloads,behavior)->"request",Collections.emptyList());
        try(ActivityScenario<ChatActivity> scenario=chat(false)) {
            scenario.onActivity(a->{
                set(a,"outbox",outbox);EditText input=a.findViewById(R.id.composerInput);input.setText("Original prompt");
                a.findViewById(R.id.sendButton).setEnabled(true);a.findViewById(R.id.sendButton).performClick();
                assertEquals("",input.getText().toString());
                a.onAck(new Ack("request","other-session",false,"Rejected"));
                assertEquals(ChatMessage.LocalState.SENDING,outbox.localReceipts().get(0).localState());
                attachments(a).add(file("new.md"));call(a,"renderAttachments");
                a.onAck(new Ack("request",SESSION,false,"Rejected"));
                assertEquals("",input.getText().toString());assertEquals("new.md",attachments(a).get(0).payload().fileName());
                a.onCommandUncertain("request",SESSION,"Late timeout");
                assertEquals(ChatMessage.LocalState.FAILED,outbox.localReceipts().get(0).localState());
                attachments(a).clear();a.onRestore(outbox.localReceipts().get(0));assertEquals("Original prompt",input.getText().toString());
            });
        }
    }

    @Test public void rejectionRestoresOnlyEmptyComposerAndAcceptanceStaysTerminal() {
        AtomicInteger calls=new AtomicInteger();
        ChatOutbox outbox=new ChatOutbox(SESSION,false,(id,text,payloads,behavior)->"request-"+calls.incrementAndGet(),Collections.emptyList());
        try(ActivityScenario<ChatActivity> scenario=chat(false)) {
            scenario.onActivity(a->{
                set(a,"outbox",outbox);EditText input=a.findViewById(R.id.composerInput);input.setText("Rejected prompt");
                a.findViewById(R.id.sendButton).setEnabled(true);a.findViewById(R.id.sendButton).performClick();
                a.onAck(new Ack("request-1",SESSION,false,"Rejected"));assertEquals("Rejected prompt",input.getText().toString());
                input.setText("Next prompt");a.findViewById(R.id.sendButton).performClick();
                a.onAck(new Ack("request-2",SESSION,true,""));a.onCommandUncertain("request-2",SESSION,"Late timeout");
                assertEquals(ChatMessage.LocalState.ACCEPTED,outbox.localReceipts().get(1).localState());
                assertEquals(2,calls.get());
            });
        }
    }

    @Test public void readOnlyCallbacksCannotSendOrConsumeReceipt() {
        AtomicInteger calls=new AtomicInteger();
        ChatMessage receipt=ChatMessage.local("cached","Old prompt",null,ChatMessage.LocalState.UNCERTAIN);
        ChatOutbox outbox=new ChatOutbox(SESSION,true,(id,text,payloads,behavior)->{calls.incrementAndGet();return "unexpected";},Collections.singletonList(receipt));
        try(ActivityScenario<ChatActivity> scenario=chat(true)) {
            scenario.onActivity(a->{set(a,"outbox",outbox);a.onRetry(receipt);a.onRestore(receipt);call(a,"onSendClicked");
                assertEquals(0,calls.get());assertEquals(Collections.singletonList(receipt),outbox.localReceipts());});
        }
    }
}
