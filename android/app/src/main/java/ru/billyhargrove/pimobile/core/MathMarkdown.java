package ru.billyhargrove.pimobile.core;

/** Normalize common math delimiters without touching fenced/inline code or prices. */
public final class MathMarkdown {
 private MathMarkdown(){}
 public static String normalize(String source){
  StringBuilder out=new StringBuilder();boolean fence=false;String fenceToken="";
  for(String line:source.split("\n",-1)){
   String trim=line.trim();if(trim.startsWith("```")||trim.startsWith("~~~")){String t=trim.substring(0,3);if(!fence){fence=true;fenceToken=t;}else if(t.equals(fenceToken))fence=false;out.append(line).append('\n');continue;}
   if(fence){out.append(line).append('\n');continue;}
   boolean code=false;
   for(int i=0;i<line.length();){char c=line.charAt(i);
    if(c=='`'){code=!code;out.append(c);i++;continue;}
    if(code){out.append(c);i++;continue;}
    if(line.startsWith("\\[",i)){out.append("\n$$\n");i+=2;continue;}
    if(line.startsWith("\\]",i)){out.append("\n$$\n");i+=2;continue;}
    if(line.startsWith("\\(",i)||line.startsWith("\\)",i)){out.append("$$");i+=2;continue;}
    if(c=='$'&&(i==0||line.charAt(i-1)!='\\')){
     if(i+1<line.length()&&line.charAt(i+1)=='$'){out.append("$$");i+=2;continue;}
     int end=line.indexOf('$',i+1);
     if(end>i+1&&!Character.isWhitespace(line.charAt(i+1))&&!Character.isWhitespace(line.charAt(end-1))){out.append("$$").append(line,i+1,end).append("$$");i=end+1;continue;}
    }
    out.append(c);i++;
   }
   out.append('\n');
  }
  return out.length()>0?out.substring(0,out.length()-1):"";
 }
}
