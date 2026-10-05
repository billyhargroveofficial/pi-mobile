package ru.billyhargrove.pimobile.ui;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.function.Supplier;
import ru.billyhargrove.pimobile.R;

/** Suggestions come from the attached Pi's resource registry, not local Android files. */
public final class SkillSuggestions {
 public SkillSuggestions(LinearLayout list,EditText input,Supplier<JSONObject> config){
  input.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){
    list.removeAllViews();list.setVisibility(View.GONE);
    String text=s.toString();if(!text.matches("\\$[a-zA-Z0-9_-]*"))return;
    JSONObject c=config.get();JSONArray skills=c==null?null:c.optJSONArray("skills");if(skills==null)return;
    String prefix=text.substring(1).toLowerCase(java.util.Locale.ROOT);int shown=0;
    for(int i=0;i<skills.length()&&shown<5;i++){JSONObject skill=skills.optJSONObject(i);if(skill==null)continue;String name=skill.optString("name");if(!name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))continue;
     TextView row=new TextView(list.getContext());row.setText("$"+name+"  ·  "+skill.optString("description"));row.setTextColor(list.getContext().getColor(R.color.text_primary));row.setTextSize(13);row.setSingleLine();row.setEllipsize(TextUtils.TruncateAt.END);int pad=Math.round(12*list.getResources().getDisplayMetrics().density);row.setPadding(pad,0,pad,0);row.setGravity(Gravity.CENTER_VERTICAL);row.setContentDescription("Навык "+name+". "+skill.optString("description"));
     row.setOnClickListener(v->{input.setText("$"+name+" ");input.setSelection(input.length());});
     list.addView(row,new LinearLayout.LayoutParams(-1,Math.round(48*list.getResources().getDisplayMetrics().density)));shown++;
    }
    if(shown>0)list.setVisibility(View.VISIBLE);
   }
   public void afterTextChanged(Editable s){}
  });
 }
}
