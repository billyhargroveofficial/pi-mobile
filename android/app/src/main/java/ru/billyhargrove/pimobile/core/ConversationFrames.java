package ru.billyhargrove.pimobile.core;
import org.json.*;import java.util.*;
/** Pure wire-frame merger. Server checkpoint is retained verbatim for conditional subscribe. */
public final class ConversationFrames {
 public static JSONObject merge(JSONObject prior,JSONObject frame)throws JSONException{
  if("snapshot".equals(frame.optString("type"))||prior==null)return new JSONObject(frame.toString());
  JSONObject out=new JSONObject(prior.toString());for(Iterator<String> it=frame.keys();it.hasNext();){String k=it.next();if(!Arrays.asList("messages","removedIds","order","resumed","type").contains(k))out.put(k,frame.get(k));}
  LinkedHashMap<String,JSONObject> rows=new LinkedHashMap<>();add(rows,prior.optJSONArray("messages"));JSONArray removed=frame.optJSONArray("removedIds");if(removed!=null)for(int i=0;i<removed.length();i++)rows.remove(removed.optString(i));add(rows,frame.optJSONArray("messages"));
  JSONArray order=frame.optJSONArray("order");if(order!=null){LinkedHashMap<String,JSONObject> tail=new LinkedHashMap<>();for(int i=0;i<order.length();i++){String id=order.optString(i);JSONObject row=rows.remove(id);if(row!=null)tail.put(id,row);}rows.putAll(tail);}
  JSONArray result=new JSONArray();int skip=Math.max(0,rows.size()-1500);for(JSONObject row:rows.values())if(skip-->0){}else result.put(row);
  if(order!=null&&result.length()>order.length()&&prior.has("history"))out.put("history",prior.get("history"));
  out.put("type","snapshot").put("messages",result);return out;
 }
 private static void add(Map<String,JSONObject> rows,JSONArray values){if(values!=null)for(int i=0;i<values.length();i++){JSONObject row=values.optJSONObject(i);if(row!=null&&!row.optString("id").isEmpty())rows.put(row.optString("id"),row);}}
 public static JSONObject prepend(JSONObject prior,JSONObject history)throws JSONException{
  if(prior==null||prior.optLong("epoch")!=history.optLong("epoch"))return prior;
  JSONObject base=new JSONObject(prior.toString()).put("messages",history.optJSONArray("messages"));JSONObject frame=new JSONObject().put("type","messages").put("messages",prior.optJSONArray("messages"));JSONObject out=merge(base,frame);if(history.has("history"))out.put("history",history.get("history"));return out;
 }
}
