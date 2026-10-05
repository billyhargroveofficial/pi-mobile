package ru.billyhargrove.pimobile.core;
import java.util.*;

/** Ordered tool segments separated by standalone progress messages and user/steer boundaries. */
public final class WorkTimeline {
 public static final class Row {
  public final String key,turn,group;public final ChatMessage message;public final boolean header,last,progress;public final int count;
  Row(String key,String turn,String group,ChatMessage message,boolean header,boolean last,boolean progress,int count){this.key=key;this.turn=turn;this.group=group;this.message=message;this.header=header;this.last=last;this.progress=progress;this.count=count;}
  public static Row body(String turn,ChatMessage first){return new Row("work-item:body:"+turn,turn,turn,first,false,true,false,0);}
  public boolean work(){return header||progress||key.startsWith("work-item:");}
 }
 public static List<Row> build(List<ChatMessage> messages,Set<String> collapsed){
  // Late nested events retain their originating turn, never leak beyond a later steer bubble.
  Map<String,List<ChatMessage>> turns=new LinkedHashMap<>();for(ChatMessage m:messages)turns.computeIfAbsent(m.turnId(),ignored->new ArrayList<>()).add(m);
  List<Row> out=new ArrayList<>();
  for(Map.Entry<String,List<ChatMessage>> entry:turns.entrySet()){
   String turn=entry.getKey();List<ChatMessage> segment=new ArrayList<>();ChatMessage answer=null;
   for(ChatMessage m:entry.getValue())if(m.role()==ChatMessage.Role.ASSISTANT)answer="work".equals(m.phase())?null:m;
   for(ChatMessage m:entry.getValue()){
    if(m==answer)continue;
    if(m.role()==ChatMessage.Role.USER||m.role()==ChatMessage.Role.ASSISTANT){
     appendTools(out,segment,turn,collapsed);segment.clear();
     boolean progress=m.role()==ChatMessage.Role.ASSISTANT;String key=(progress?"progress:":"")+m.stableKey();
     out.add(new Row(key,turn,key,m,false,false,progress,0));
    }else segment.add(m);
   }
   appendTools(out,segment,turn,collapsed);
   if(answer!=null)out.add(new Row(answer.stableKey(),turn,answer.stableKey(),answer,false,false,false,0));
  }
  return out;
 }
 private static void appendTools(List<Row> out,List<ChatMessage> tools,String turn,Set<String> collapsed){
  if(tools.isEmpty())return;String group="tools:"+turn+":"+tools.get(0).stableKey();boolean closed=collapsed.contains(group);
  out.add(new Row("work-header:"+group,turn,group,null,true,closed,false,tools.size()));
  if(!closed)for(int i=0;i<tools.size();i++)out.add(new Row("work-item:"+tools.get(i).stableKey(),turn,group,tools.get(i),false,i==tools.size()-1,false,0));
 }
}
