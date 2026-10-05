package ru.billyhargrove.pimobile.ui;
import android.graphics.*;
import androidx.recyclerview.widget.*;
import java.util.function.*;
import ru.billyhargrove.pimobile.R;
/** Swiping exposes an action; the caller confirms it before changing authoritative data. */
public final class SwipeAction {
 private SwipeAction(){}
 public static void attach(RecyclerView list,String label,IntPredicate allowed,IntConsumer action){
  new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,ItemTouchHelper.LEFT){
   private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
   @Override public int getSwipeDirs(RecyclerView r,RecyclerView.ViewHolder h){int p=h.getBindingAdapterPosition();return p!=RecyclerView.NO_POSITION&&allowed.test(p)?ItemTouchHelper.LEFT:0;}
   @Override public boolean onMove(RecyclerView r,RecyclerView.ViewHolder a,RecyclerView.ViewHolder b){return false;}
   @Override public void onSwiped(RecyclerView.ViewHolder h,int direction){int p=h.getBindingAdapterPosition();if(p==RecyclerView.NO_POSITION)return;list.getAdapter().notifyItemChanged(p);action.accept(p);}
   @Override public void onChildDraw(Canvas c,RecyclerView r,RecyclerView.ViewHolder h,float dx,float dy,int state,boolean active){
    if(dx<0){android.view.View v=h.itemView;float d=v.getResources().getDisplayMetrics().density;paint.setColor(v.getContext().getColor(R.color.danger_soft));c.drawRoundRect(v.getLeft(),v.getTop(),v.getRight(),v.getBottom(),20*d,20*d,paint);paint.setColor(v.getContext().getColor(R.color.danger));paint.setTextSize(14*v.getResources().getDisplayMetrics().scaledDensity);paint.setTextAlign(Paint.Align.RIGHT);c.drawText(label,v.getRight()-24*d,(v.getTop()+v.getBottom())/2f-(paint.ascent()+paint.descent())/2,paint);}
    super.onChildDraw(c,r,h,dx,dy,state,active);
   }
  }).attachToRecyclerView(list);
 }
}
