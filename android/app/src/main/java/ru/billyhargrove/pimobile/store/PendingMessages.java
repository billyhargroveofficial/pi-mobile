package ru.billyhargrove.pimobile.store;
import android.content.Context;
import org.json.*;
import java.util.*;
import ru.billyhargrove.pimobile.core.*;

/** Private, host-scoped text receipts. Never auto-replays an uncertain command. */
public final class PendingMessages {
 private final android.content.SharedPreferences preferences;private final String key;private String last="";
 public PendingMessages(Context context,String host,String session){preferences=context.getSharedPreferences("mobile-outbox",Context.MODE_PRIVATE);key=host+"|"+session;}
 public List<ChatMessage> load(){List<ChatMessage> result=new ArrayList<>();try{JSONArray records=new JSONArray(preferences.getString(key,"[]"));for(int i=0;i<records.length();i++){JSONObject r=records.getJSONObject(i);ChatMessage.LocalState state=ChatMessage.LocalState.valueOf(r.getString("state"));if(state==ChatMessage.LocalState.SENDING)state=ChatMessage.LocalState.UNCERTAIN;List<ImageRef> images=new ArrayList<>();JSONArray a=r.optJSONArray("images");if(a!=null)for(int j=0;j<a.length();j++)images.add(new ImageRef("local:"+j,a.getString(j)));result.add(ChatMessage.local(r.getString("id"),r.getString("text"),images,state));}}catch(Exception ignored){}return result;}
 public void save(List<ChatMessage> messages){try{JSONArray records=new JSONArray();for(ChatMessage m:messages){JSONArray images=new JSONArray();for(ImageRef i:m.images())images.put(i.mimeType());records.put(new JSONObject().put("id",m.requestId()).put("text",m.text()).put("state",m.localState().name()).put("images",images));}String value=records.toString();if(!value.equals(last)){last=value;if(messages.isEmpty())preferences.edit().remove(key).apply();else preferences.edit().putString(key,value).apply();}}catch(JSONException ignored){}}
}
