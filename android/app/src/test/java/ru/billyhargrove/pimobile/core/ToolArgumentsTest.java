package ru.billyhargrove.pimobile.core;
import org.junit.Test;import static org.junit.Assert.*;
public class ToolArgumentsTest {
 @Test public void commandAndPathAreReadable(){assertEquals("git status",ToolArguments.format("bash","{\"command\":\"git status\"}"));assertEquals("~/repo/main.java · со строки 20 · 40 строк",ToolArguments.format("read","{\"path\":\"/Users/me/repo/main.java\",\"offset\":20,\"limit\":40}"));}
 @Test public void editsAndQueriesHaveNoJson(){assertEquals("main.java · правок: 2",ToolArguments.format("edit","{\"path\":\"main.java\",\"edits\":[{},{}]}"));assertEquals("native Android · RecyclerView",ToolArguments.format("web","{\"search_query\":[{\"q\":\"native Android\"},{\"q\":\"RecyclerView\"}]}"));}
 @Test public void credentialsAndMalformedJsonNeverLeak(){String s=ToolArguments.format("mcp","{\"tool\":\"search\",\"api_key\":\"SECRET\",\"headers\":{\"Authorization\":\"SECRET\"}}");assertEquals("tool: search",s);assertEquals("Аргументы недоступны",ToolArguments.format("read","{broken"));assertEquals("plain preview",ToolArguments.format("tool","plain preview"));}
}
