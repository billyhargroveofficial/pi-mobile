package ru.billyhargrove.pimobile.core;

import org.json.*;
import java.util.*;

/** Human-readable single-line arguments; never print JSON containers or credential values. */
public final class ToolArguments {
 private ToolArguments(){}
 public static String format(String tool,String raw){
  if(raw==null||raw.trim().isEmpty())return "";
  String input=raw.trim();if(!input.startsWith("{")&&!input.startsWith("["))return line(input);
  try{Object parsed=new JSONTokener(input).nextValue();if(!(parsed instanceof JSONObject))return value(parsed);
   JSONObject args=(JSONObject)parsed;String name=tool==null?"":tool.toLowerCase(Locale.ROOT);
   if(name.contains("bash")||name.contains("shell"))return first(args,"command","cmd","script");
   String path=first(args,"path","file_path","filePath");
   if(!path.isEmpty()){
    List<String> parts=new ArrayList<>();parts.add(path.replaceFirst("^/(Users|home)/[^/]+/","~/"));
    if(args.has("offset"))parts.add("со строки "+value(args.opt("offset")));
    if(args.has("limit"))parts.add(value(args.opt("limit"))+" строк");
    JSONArray edits=args.optJSONArray("edits");if(edits!=null)parts.add("правок: "+edits.length());
    if(args.has("pattern"))parts.add("найти: "+value(args.opt("pattern")));
    return String.join(" · ",parts);
   }
   for(String key:new String[]{"search_query","image_query","queries"}){JSONArray queries=args.optJSONArray(key);if(queries!=null){List<String> parts=new ArrayList<>();for(int i=0;i<Math.min(queries.length(),4);i++){Object q=queries.opt(i);parts.add(q instanceof JSONObject?first((JSONObject)q,"q","query"):value(q));}return String.join(" · ",parts);}}
   String query=first(args,"q","query","pattern");if(!query.isEmpty())return query;
   List<String> parts=new ArrayList<>();Iterator<String> keys=args.keys();while(keys.hasNext()&&parts.size()<4){String key=keys.next();if(secret(key))continue;String v=value(args.opt(key));if(!v.isEmpty())parts.add(key+": "+v);}return String.join(" · ",parts);
  }catch(JSONException e){return "Аргументы недоступны";}
 }
 private static boolean secret(String key){return key.toLowerCase(Locale.ROOT).matches(".*(token|password|secret|authorization|api.?key|headers).*");}
 private static String first(JSONObject o,String...keys){for(String key:keys){String v=value(o.opt(key));if(!v.isEmpty())return v;}return "";}
 private static String value(Object o){if(o==null||o==JSONObject.NULL)return "";if(o instanceof JSONObject){JSONObject obj=(JSONObject)o;List<String> parts=new ArrayList<>();Iterator<String> it=obj.keys();while(it.hasNext()&&parts.size()<3){String k=it.next();if(!secret(k)){Object v=obj.opt(k);if(!(v instanceof JSONObject)&&!(v instanceof JSONArray))parts.add(k+": "+line(String.valueOf(v)));}}return parts.isEmpty()?"параметры":String.join(", ",parts);}if(o instanceof JSONArray){JSONArray a=(JSONArray)o;List<String> p=new ArrayList<>();for(int i=0;i<Math.min(a.length(),3);i++)p.add(value(a.opt(i)));if(a.length()>3)p.add("ещё "+(a.length()-3));return String.join(", ",p);}return line(String.valueOf(o));}
 private static String line(String s){String value=s.replaceAll("\\s+"," ").trim();return value.length()>500?value.substring(0,497)+"…":value;}
}
