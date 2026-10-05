package ru.billyhargrove.pimobile.ui;

import android.media.*;
import android.os.*;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

/** Explicit foreground-only PCM capture; never sends text to Pi automatically. */
public final class DictationRecorder {
 private volatile boolean recording,cancelled;private AudioRecord recorder;private AlertDialog dialog;
 private final Handler main=new Handler(Looper.getMainLooper());private final Consumer<byte[]> ready;private final Consumer<String> error;
 public DictationRecorder(Context c,Consumer<byte[]> ready,Consumer<String> error){this.ready=ready;this.error=error;
  try{int minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(8192,minimum));if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Microphone unavailable");recorder.startRecording();recording=true;
   dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(c).setTitle("Dictation").setMessage("Recording · up to 60 seconds\nTranscribed text will appear in the composer. Nothing is sent to Pi automatically.").setPositiveButton("Done",(d,w)->stop()).setNegativeButton("Cancel",(d,w)->cancel()).create();dialog.setOnCancelListener(d->cancel());dialog.show();
   new Thread(this::capture,"pi-mobile-microphone").start();main.postDelayed(this::stop,60000);
  }catch(Exception e){cancel();if(recorder!=null){recorder.release();recorder=null;}error.accept(e.getMessage());}
 }
 private void capture(){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[3200];String failure=null;
  try{while(recording&&out.size()<1920000){int n=recorder.read(buffer,0,Math.min(buffer.length,1920000-out.size()));if(n<0){if(recording)throw new IllegalStateException("Could not capture microphone audio");break;}if(n>0)out.write(buffer,0,n);}}
  catch(Exception e){if(!cancelled)failure=e.getMessage();}finally{recording=false;try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}
  byte[] bytes=out.toByteArray();String result=failure;main.post(()->{main.removeCallbacksAndMessages(null);if(dialog!=null)dialog.dismiss();if(cancelled)return;if(result!=null)error.accept(result);else if(bytes.length<3200)error.accept("Recording is too short");else ready.accept(bytes);});
 }
 private void stop(){recording=false;}
 public void cancel(){cancelled=true;recording=false;main.removeCallbacksAndMessages(null);if(dialog!=null)dialog.dismiss();if(recorder!=null)try{recorder.stop();}catch(Exception ignored){}}
}
