package ru.billyhargrove.pimobile.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import ru.billyhargrove.pimobile.R;

/** Offers only authenticated models and supported efforts reported by this live Pi. */
public final class ModelSettingsSheet extends BottomSheetDialog {
    public interface Apply { void apply(String provider,String model,String effort); }
    private final MaterialButton apply;
    private final TextView status;
    private JSONObject selected;
    private final List<JSONObject> models=new ArrayList<>();
    private final List<String> labels=new ArrayList<>();
    private final MaterialAutoCompleteTextView modelInput,effortInput;
    private boolean updating;

    public ModelSettingsSheet(Context context,JSONObject config,boolean idle,Apply callback){
        super(context);
        LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);int p=dp(20);root.setPadding(p,p,p,p);
        TextView title=new TextView(context);title.setText("Модель и effort");title.setTextSize(22);title.setTextColor(context.getColor(R.color.text_primary));root.addView(title);
        status=new TextView(context);status.setTextSize(13);status.setPadding(0,dp(8),0,dp(16));status.setTextColor(context.getColor(R.color.text_secondary));root.addView(status);
        status.setText(!idle?"Дождись завершения задачи. Изменения касаются только этого диалога.":"Только текущий диалог. Настройки новых Pi не меняются.");
        TextInputLayout modelBox=new TextInputLayout(context,null,com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);modelBox.setHint("Модель · поиск по имени");
        modelInput=new MaterialAutoCompleteTextView(context);modelInput.setId(R.id.modelSelector);modelInput.setSingleLine(true);modelInput.setThreshold(0);modelInput.setTextSize(14);modelBox.addView(modelInput,new LinearLayout.LayoutParams(-1,-2));root.addView(modelBox,new LinearLayout.LayoutParams(-1,-2));
        TextInputLayout effortBox=new TextInputLayout(context,null,com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);effortBox.setHint("Effort");
        effortInput=new MaterialAutoCompleteTextView(context);effortInput.setId(R.id.effortSelector);effortInput.setInputType(android.text.InputType.TYPE_NULL);effortBox.addView(effortInput,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.topMargin=dp(12);root.addView(effortBox,ep);
        apply=new MaterialButton(context);apply.setId(R.id.applyModelButton);apply.setText("Применить");LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(52));bp.topMargin=dp(16);root.addView(apply,bp);
        JSONArray array=config.optJSONArray("models");
        if(array!=null)for(int i=0;i<array.length();i++){JSONObject m=array.optJSONObject(i);if(m==null)continue;models.add(m);labels.add(m.optString("id")+" · "+m.optString("provider"));if((m.optString("provider")+"/"+m.optString("id")).equals(config.optString("model")))selected=m;}
        modelInput.setAdapter(new ArrayAdapter<>(context,R.layout.item_dropdown,labels));
        if(selected!=null){modelInput.setText(label(selected),false);efforts(selected,config.optString("thinkingLevel","off"));}
        if(models.isEmpty())status.setText("Нет списка моделей. Выполни /reload в этом Pi, когда он освободится.");
        else if(config.optBoolean("modelsTruncated"))status.append(" Список моделей ограничен первыми 1000.");
        modelInput.setOnItemClickListener((parent,view,pos,id)->{String chosen=String.valueOf(parent.getItemAtPosition(pos));int index=labels.indexOf(chosen);if(index>=0){selected=models.get(index);efforts(selected,config.optString("thinkingLevel","off"));}apply.setEnabled(idle&&selected!=null);});
        modelInput.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){if(!updating&&selected!=null&&!s.toString().equals(label(selected))){selected=null;apply.setEnabled(false);}}public void afterTextChanged(Editable e){}});
        apply.setEnabled(idle&&selected!=null);modelInput.setEnabled(idle);effortInput.setEnabled(idle);
        apply.setOnClickListener(v->{if(selected==null)return;apply.setEnabled(false);status.setText("Ожидаю подтверждение Pi…");callback.apply(selected.optString("provider"),selected.optString("id"),effortInput.getText().toString());});
        setContentView(root);
        setOnShowListener(d->{getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);getBehavior().setSkipCollapsed(true);});
        ExpressiveMotion.buttons(root);
    }
    private void efforts(JSONObject model,String preferred){JSONArray a=model.optJSONArray("thinkingLevels");List<String> levels=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++)levels.add(a.optString(i));effortInput.setAdapter(new ArrayAdapter<>(getContext(),R.layout.item_dropdown,levels));effortInput.setText(levels.contains(preferred)?preferred:levels.isEmpty()?"off":levels.get(0),false);}
    public void failed(String message){status.setText(message);status.setTextColor(getContext().getColor(R.color.danger));apply.setEnabled(selected!=null);}
    private String label(JSONObject m){return m.optString("id")+" · "+m.optString("provider");}
    private int dp(int value){return Math.round(value*getContext().getResources().getDisplayMetrics().density);}
}
