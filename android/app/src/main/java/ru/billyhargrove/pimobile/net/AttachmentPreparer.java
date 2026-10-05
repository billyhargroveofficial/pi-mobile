package ru.billyhargrove.pimobile.net;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.*;
import ru.billyhargrove.pimobile.core.ImagePayload;
import ru.billyhargrove.pimobile.media.ImagePreparer;

/** Read only explicitly picked content URIs; bound bytes while reading, not after allocation. */
public final class AttachmentPreparer {
 private AttachmentPreparer(){}
 public static ImagePayload prepare(ContentResolver resolver,Uri uri,long budget)throws Exception{
  String mime=resolver.getType(uri),name=ImagePreparer.displayName(resolver,uri);
  if(mime!=null&&mime.startsWith("image/"))return ImagePreparer.prepare(resolver,uri,budget);
  if(name==null||name.isBlank())name="file";
  name=name.replaceAll("[\\\\/\\x00-\\x1f]","_");if(name.length()>200)name=name.substring(0,200);
  try(InputStream input=resolver.openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
   if(input==null)throw new IOException("Could not open file");byte[] buffer=new byte[8192];long read=0;int n;
   while((n=input.read(buffer))!=-1){read+=n;if(read>budget)throw new IOException("Attachments must total no more than 10 MB");out.write(buffer,0,n);}
   if(read==0)throw new IOException("Empty file");return ImagePayload.file(out.toByteArray(),name);
  }
 }
}
