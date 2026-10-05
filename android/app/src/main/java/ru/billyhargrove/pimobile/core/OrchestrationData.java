package ru.billyhargrove.pimobile.core;

import org.json.*;
import java.util.*;

/** Read-only projections of the installed subagent runner; no inferred percent-complete. */
public final class OrchestrationData {
 public static boolean active(String status){return Arrays.asList("running","queued","starting","waiting").contains(status);}
 public static String label(String status){switch(status){case "running":return "Running";case "queued":return "Queued";case "starting":return "Starting";case "waiting":return "Waiting";case "completed":return "Done";case "failed":return "Failed";case "cancelled":return "Stopped";case "paused":return "Paused";default:return "Unknown";}}
 public static List<JSONObject> objects(JSONArray array){List<JSONObject> out=new ArrayList<>();if(array!=null)for(int i=0;i<array.length();i++){JSONObject x=array.optJSONObject(i);if(x!=null)out.add(x);}return out;}
 public static List<JSONObject> agents(JSONObject data,String workflow){List<JSONObject> out=new ArrayList<>();for(JSONObject a:objects(data.optJSONArray("agents")))if(workflow==null||workflow.equals(a.optString("workflowId")))out.add(a);return out;}
 public static int running(JSONObject data){int count=0;for(JSONObject a:agents(data,null))if("running".equals(a.optString("status")))count++;return count;}
 public static String summary(JSONObject data){int agents=agents(data,null).size(),flows=objects(data.optJSONArray("workflows")).size();return (flows>0?flows+" workflow"+(flows==1?"":"s")+" · ":"")+agents+" agent"+(agents==1?"":"s")+(running(data)>0?" · "+running(data)+" running":"");}
 public static String shortModel(String model){int at=model.lastIndexOf('/');return model.substring(at+1);}
 public static String duration(JSONObject a,long now){long start=a.optLong("startedAt"),end=a.optLong("finishedAt");if(start<=0)return "";long seconds=Math.max(0,((end>0?end:now)-start)/1000);return seconds>=3600?(seconds/3600)+"h "+((seconds%3600)/60)+"m":seconds>=60?(seconds/60)+"m "+(seconds%60)+"s":seconds+"s";}
 public static String details(JSONObject a){List<String> parts=new ArrayList<>();String model=shortModel(a.optString("model"));if(!model.isEmpty())parts.add(model);String effort=a.optString("thinkingLevel");if(!effort.isEmpty())parts.add(effort);String duration=duration(a,System.currentTimeMillis());if(!duration.isEmpty())parts.add(duration);long tokens=a.optLong("outputTokens");if(tokens>0)parts.add(tokens+" output tokens");return String.join(" · ",parts);}
}
