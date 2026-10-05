package ru.billyhargrove.pimobile.core;
import org.json.*;
/** Selected next-request settings are distinct from the configuration captured for an active request. */
public final class ExecutionInfo {
 public final String model,effort,tier;public final boolean confirmed,available;
 public ExecutionInfo(JSONObject configuration,boolean running){
  JSONObject source=configuration==null?new JSONObject():configuration;JSONObject active=running?source.optJSONObject("execution"):null;
  if(active!=null&&!active.optString("model").isEmpty())source=active;
  String name=source.optString("model","");model=name.substring(name.lastIndexOf('/')+1);
  effort=source.optString("thinkingLevel","off");tier=source.optString("serviceTier","unknown");confirmed=source.optBoolean("tierConfirmed");
  JSONArray tiers=configuration==null?null:configuration.optJSONArray("serviceTiers");available=active!=null?!"unknown".equals(tier):tiers!=null&&tiers.length()>0;
 }
 public String tierLabel(boolean running){if(!available)return "Tier unavailable";return "fast".equals(tier)?(running&&!confirmed?"Fast requested":"Fast"):"Standard";}
}
