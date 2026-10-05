package ru.billyhargrove.pimobile.core;
import java.util.*;

/** Flat RecyclerView rows form one surface per turn, including late nested tool events. */
public final class WorkTimeline {
 public static final class Row {
  public final String key,turn;public final ChatMessage message;public final boolean header,last;public final int count;
  Row(String key,String turn,ChatMessage message,boolean header,boolean last,int count){this.key=key;this.turn=turn;this.message=message;this.header=header;this.last=last;this.count=count;}
  public boolean work(){return header||key.startsWith("work-item:");}
 }
 public static List<Row> build(List<ChatMessage> messages,Set<String> collapsed){
  Map<String,List<ChatMessage>> groups=new LinkedHashMap<>();
  for(ChatMessage m:messages)groups.computeIfAbsent(m.turnId(),ignored->new ArrayList<>()).add(m);
  List<Row> out=new ArrayList<>();
  for(Map.Entry<String,List<ChatMessage>> entry:groups.entrySet()){
   String turn=entry.getKey();List<ChatMessage> group=entry.getValue(),work=new ArrayList<>();ChatMessage answer=null;int tools=0;
   for(ChatMessage m:group)if(m.role()==ChatMessage.Role.ASSISTANT)answer="work".equals(m.phase())?null:m;
   for(ChatMessage m:group){if(m.role()==ChatMessage.Role.USER)out.add(new Row(m.stableKey(),turn,m,false,false,0));else if(m!=answer){work.add(m);if(m.role()==ChatMessage.Role.TOOL_RESULT)tools++;}}
   if(!work.isEmpty()){
    out.add(new Row("work-header:"+turn,turn,null,true,collapsed.contains(turn),tools));
    if(!collapsed.contains(turn))for(int i=0;i<work.size();i++)out.add(new Row("work-item:"+work.get(i).stableKey(),turn,work.get(i),false,i==work.size()-1,0));
   }
   if(answer!=null)out.add(new Row(answer.stableKey(),turn,answer,false,false,0));
  }
  return out;
 }
}
