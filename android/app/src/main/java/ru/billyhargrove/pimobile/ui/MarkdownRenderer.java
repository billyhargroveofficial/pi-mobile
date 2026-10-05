package ru.billyhargrove.pimobile.ui;
import android.content.Context;
import android.text.Spanned;
import android.widget.TextView;
import io.noties.markwon.*;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import ru.billyhargrove.pimobile.core.MathMarkdown;
import ru.billyhargrove.pimobile.R;

/** Native text + native formula drawables. No WebView, HTML, JS or remote image loading. */
public final class MarkdownRenderer {
 public interface Links{void open(String path);}
 private final Markwon markwon;
 private volatile boolean closed;
 private final android.util.LruCache<String,Spanned> cache=new android.util.LruCache<>(48);
 private final java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();
 private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
 public MarkdownRenderer(Context c,Links links){
  markwon=Markwon.builder(c)
   .usePlugin(MarkwonInlineParserPlugin.create())
   .usePlugin(JLatexMathPlugin.create(16*c.getResources().getDisplayMetrics().scaledDensity,b->{b.inlinesEnabled(true);b.executorService(executor);b.theme().textColor(c.getColor(R.color.text_primary));}))
   .usePlugin(TablePlugin.create(c)).usePlugin(StrikethroughPlugin.create()).usePlugin(TaskListPlugin.create(c))
   .usePlugin(new AbstractMarkwonPlugin(){@Override public void configureConfiguration(MarkwonConfiguration.Builder b){b.linkResolver((view,link)->links.open(link));}}).build();
 }
 public void render(TextView view,String source){
  if(closed)return;
  view.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
  String key=source==null?"":source;
  if(key.equals(view.getTag(R.id.markdownSource)))return;
  view.setTag(R.id.markdownSource,key);
  Spanned stored=cache.get(key);
  if(stored!=null){markwon.setParsedMarkdown(view,stored);return;}
  // A recycled holder must never show the previous message while parsing.
  view.setText(key);
  executor.execute(()->{
   if(closed)return;
   Spanned parsed;
   try{String normalized=MathMarkdown.normalize(key);if(normalized.matches("(?s).*\\\\(includegraphics|input|include|write|href|url)\\b.*"))parsed=new android.text.SpannableString(key);else parsed=markwon.toMarkdown(normalized);}catch(Throwable error){return;}
   cache.put(key,parsed);main.post(()->{if(!closed&&key.equals(view.getTag(R.id.markdownSource)))markwon.setParsedMarkdown(view,parsed);});
  });
 }
 public void close(){closed=true;executor.shutdownNow();cache.evictAll();}
}
