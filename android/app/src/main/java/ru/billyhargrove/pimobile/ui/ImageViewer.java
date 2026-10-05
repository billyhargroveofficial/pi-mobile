package ru.billyhargrove.pimobile.ui;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.widget.AppCompatImageView;
import com.google.android.material.button.MaterialButton;
import ru.billyhargrove.pimobile.PiApp;
import ru.billyhargrove.pimobile.R;
import ru.billyhargrove.pimobile.net.MediaLoader;

/** Full-screen authenticated image viewer; pinch/pan and double-tap, no file URLs. */
public final class ImageViewer extends AppCompatDialogFragment {
    private Bitmap localBitmap;
    public static void show(android.content.Context context, String url, Bitmap local) {
        while (!(context instanceof AppCompatActivity) && context instanceof android.content.ContextWrapper) {
            android.content.Context base=((android.content.ContextWrapper)context).getBaseContext();
            if(base==context)break;
            context=base;
        }
        if (!(context instanceof AppCompatActivity)) return;
        ImageViewer viewer=new ImageViewer(); Bundle args=new Bundle();args.putString("url",url);viewer.setArguments(args);viewer.localBitmap=local;
        viewer.show(((AppCompatActivity)context).getSupportFragmentManager(),"image-viewer");
    }
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        Dialog dialog=new Dialog(requireContext(),R.style.Theme_PiMobile);
        FrameLayout root=new FrameLayout(requireContext());root.setBackgroundColor(getResources().getColor(R.color.bg,null));
        ZoomImage image=new ZoomImage(requireContext());image.setId(R.id.zoomImage);image.setContentDescription("Image. Pinch or double-tap to zoom");
        root.addView(image,new FrameLayout.LayoutParams(-1,-1));
        TextView error=new TextView(requireContext());error.setGravity(Gravity.CENTER);error.setTextColor(getResources().getColor(R.color.danger,null));root.addView(error,new FrameLayout.LayoutParams(-1,-1));error.setVisibility(View.GONE);
        MaterialButton close=(MaterialButton)android.view.LayoutInflater.from(requireContext()).inflate(R.layout.icon_button,root,false);
        ExpressiveMotion.press(close);
        close.setId(R.id.closeImageButton);close.setIconResource(R.drawable.ic_back);close.setContentDescription("Close image");close.setOnClickListener(v->dismiss());
        int size=(int)(48*getResources().getDisplayMetrics().density);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(size,size,Gravity.TOP|Gravity.START);p.setMargins(size/3,size/3,0,0);root.addView(close,p);
        dialog.setContentView(root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{androidx.core.graphics.Insets b=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        String url=requireArguments().getString("url","");
        if(localBitmap!=null)image.setImageBitmap(localBitmap);
        else PiApp.get(requireContext()).mediaLoader().load(url,new MediaLoader.Callback(){
            public void onLoaded(String resolved,Bitmap bitmap){if(isAdded())image.setImageBitmap(bitmap);}
            public void onFailed(String resolved,String message){if(isAdded()){error.setText(message);error.setVisibility(View.VISIBLE);}}
        });
        return dialog;
    }
    @Override public void onStart(){super.onStart();if(getDialog()!=null&&getDialog().getWindow()!=null)getDialog().getWindow().setLayout(-1,-1);}

    static final class ZoomImage extends AppCompatImageView {
        final Matrix transform=new Matrix(); float fit=1,scale=1,lastX,lastY;boolean moved;
        final ScaleGestureDetector pinch; final GestureDetector taps;
        ZoomImage(android.content.Context c){super(c);setScaleType(ScaleType.MATRIX);setClickable(true);
            pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector d){zoom(d.getScaleFactor(),d.getFocusX(),d.getFocusY());return true;}
            });
            taps=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onDown(MotionEvent e){return true;}
                @Override public boolean onDoubleTap(MotionEvent e){if(scale>fit*1.1f)reset();else zoom(2.5f,e.getX(),e.getY());return true;}
            });
        }
        @Override public void setImageBitmap(Bitmap b){super.setImageBitmap(b);post(this::reset);}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);reset();}
        void reset(){if(getDrawable()==null||getWidth()==0)return;float w=getDrawable().getIntrinsicWidth(),h=getDrawable().getIntrinsicHeight();fit=Math.min(getWidth()/w,getHeight()/h);scale=fit;transform.reset();transform.postScale(fit,fit);transform.postTranslate((getWidth()-w*fit)/2,(getHeight()-h*fit)/2);setImageMatrix(transform);}
        void zoom(float factor,float x,float y){float next=Math.max(fit,Math.min(fit*6,scale*factor));transform.postScale(next/scale,next/scale,x,y);scale=next;bound();}
        void bound(){if(getDrawable()==null)return;RectF r=new RectF(0,0,getDrawable().getIntrinsicWidth(),getDrawable().getIntrinsicHeight());transform.mapRect(r);float dx=r.width()<=getWidth()?(getWidth()-r.width())/2-r.left:r.left>0?-r.left:r.right<getWidth()?getWidth()-r.right:0;float dy=r.height()<=getHeight()?(getHeight()-r.height())/2-r.top:r.top>0?-r.top:r.bottom<getHeight()?getHeight()-r.bottom:0;transform.postTranslate(dx,dy);setImageMatrix(transform);}
        @Override public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);taps.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();moved=false;}else if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(!pinch.isInProgress()&&e.getPointerCount()==1){float dx=e.getX()-lastX,dy=e.getY()-lastY;if(Math.abs(dx)+Math.abs(dy)>3)moved=true;transform.postTranslate(dx,dy);bound();}lastX=e.getX();lastY=e.getY();}else if(e.getActionMasked()==MotionEvent.ACTION_UP&&!moved)performClick();return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
