package ru.billyhargrove.pimobile.core;
public final class DurationText {
 private DurationText(){}
 public static String format(long ms){long s=Math.max(0,ms)/1000,h=s/3600,m=(s%3600)/60;return (h>0?h+"h ":"")+(h>0||m>0?m+"m ":"")+(s%60)+"s";}
}
