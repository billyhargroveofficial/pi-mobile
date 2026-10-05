package ru.billyhargrove.pimobile.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.media.*;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.io.*;
import java.util.Locale;
import java.util.function.Consumer;
import ru.billyhargrove.pimobile.R;

/** Explicit foreground capture, streamed to a private temporary file. Never sends a Pi prompt. */
public final class DictationRecorder {
    public static final int MAX_SECONDS = 600;
    public static final int SAMPLE_RATE = 16000;
    public static final int MAX_BYTES = SAMPLE_RATE * 2 * MAX_SECONDS;
    private volatile boolean recording, cancelled;
    private AudioRecord recorder;
    private BottomSheetDialog dialog;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Consumer<File> ready;
    private final Consumer<String> error;
    private File audio;
    private VoiceWaveform waveform;
    private TextView timer;

    public DictationRecorder(Context context, Consumer<File> ready, Consumer<String> error) {
        this.ready=ready; this.error=error;
        try {
            File dir=new File(context.getCacheDir(),"dictation");
            if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create recording file");
            audio=File.createTempFile("recording-",".pcm",dir);
            int minimum=AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(minimum<=0)throw new IOException("Microphone unavailable");
            recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(8192,minimum));
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Microphone unavailable");
            recorder.startRecording();recording=true;
            show(context);
            AudioRecord source=recorder;
            new Thread(()->capture(source),"pi-mobile-microphone").start();
            main.postDelayed(this::stop,MAX_SECONDS*1000L);
        } catch(Exception failure) {
            cancelled=true;recording=false;
            if(recorder!=null){try{recorder.release();}catch(Exception ignored){}recorder=null;}
            if(audio!=null)audio.delete();if(dialog!=null)dialog.dismiss();
            main.post(()->error.accept(failure.getMessage()==null?"Microphone unavailable":failure.getMessage()));
        }
    }
    private int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    private TextView label(Context c,String text,int size,int color){TextView v=new TextView(c);v.setText(text);v.setTextSize(size);v.setTextColor(c.getColor(color));v.setGravity(Gravity.CENTER);return v;}
    private void show(Context context) {
        LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(context,24),dp(context,16),dp(context,24),dp(context,24));
        ViewSpace.addGrip(root);
        TextView title=label(context,"Listening",20,R.color.text_primary);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        timer=label(context,"00:00",36,R.color.text_primary);timer.setTypeface(Typeface.MONOSPACE);LinearLayout.LayoutParams timeParams=new LinearLayout.LayoutParams(-1,-2);timeParams.topMargin=dp(context,24);root.addView(timer,timeParams);
        waveform=new VoiceWaveform(context);waveform.setId(R.id.voiceWaveform);LinearLayout.LayoutParams waveParams=new LinearLayout.LayoutParams(-1,dp(context,88));waveParams.topMargin=dp(context,12);waveParams.bottomMargin=dp(context,16);root.addView(waveform,waveParams);
        root.addView(label(context,"Up to 10 minutes · stays in your draft",13,R.color.text_secondary));
        LinearLayout actions=new LinearLayout(context);actions.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,dp(context,56));rowParams.topMargin=dp(context,24);root.addView(actions,rowParams);
        MaterialButton cancel=new MaterialButton(context,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);cancel.setText("Cancel");LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1);left.setMarginEnd(dp(context,12));actions.addView(cancel,left);cancel.setOnClickListener(v->cancel());
        MaterialButton finish=new MaterialButton(context);finish.setText("Finish");actions.addView(finish,new LinearLayout.LayoutParams(0,-1,1));finish.setOnClickListener(v->{finish.setEnabled(false);finish.setText("Finishing…");stop();});
        dialog=new BottomSheetDialog(context);dialog.setContentView(root);dialog.setOnCancelListener(d->cancel());dialog.getBehavior().setSkipCollapsed(true);dialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);dialog.show();
    }
    private void capture(AudioRecord source) {
        byte[] buffer=new byte[3200];String failure=null;int total=0;
        try(OutputStream output=new BufferedOutputStream(new FileOutputStream(audio))) {
            while(recording&&total<MAX_BYTES){
                int n=source.read(buffer,0,Math.min(buffer.length,MAX_BYTES-total));
                if(n<0){if(recording)throw new IOException("Could not capture microphone audio");break;}
                if(n==0)continue;
                output.write(buffer,0,n);total+=n;
                double sum=0;for(int i=0;i+1<n;i+=2){short value=(short)((buffer[i]&255)|(buffer[i+1]<<8));double sample=value/32768.0;sum+=sample*sample;}
                float level=(float)Math.min(1,Math.sqrt(sum/Math.max(1,n/2))*4);
                int seconds=total/(SAMPLE_RATE*2);
                main.post(()->{if(!cancelled&&waveform!=null){waveform.sample(level);timer.setText(String.format(Locale.ENGLISH,"%02d:%02d",seconds/60,seconds%60));}});
            }
        } catch(Exception e){if(!cancelled)failure=e.getMessage();}
        finally{recording=false;try{source.stop();}catch(Exception ignored){}source.release();}
        String problem=failure;int count=total;
        main.post(()->{recorder=null;main.removeCallbacksAndMessages(null);if(dialog!=null)dialog.dismiss();if(cancelled){audio.delete();return;}if(problem!=null||count<3200){audio.delete();error.accept(problem==null?"Recording is too short":problem);}else ready.accept(audio);});
    }
    private void stop(){recording=false;AudioRecord source=recorder;if(source!=null)try{source.stop();}catch(Exception ignored){}}
    public void cancel(){cancelled=true;main.removeCallbacksAndMessages(null);stop();if(dialog!=null)dialog.dismiss();}
    private static final class ViewSpace {
        static void addGrip(LinearLayout root){Context c=root.getContext();float d=c.getResources().getDisplayMetrics().density;android.view.View grip=new android.view.View(c);grip.setBackground(BubbleColors.background(c,c.getColor(R.color.outline_soft)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(Math.round(32*d),Math.round(4*d));p.gravity=Gravity.CENTER_HORIZONTAL;p.bottomMargin=Math.round(24*d);root.addView(grip,p);}
    }
}
