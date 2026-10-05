package ru.billyhargrove.pimobile.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class DurationTextTest {
 @Test public void durationUsesOnlyNeededUnits(){
  assertEquals("0s",DurationText.format(-1));
  assertEquals("59s",DurationText.format(59999));
  assertEquals("1m 0s",DurationText.format(60000));
  assertEquals("26m 28s",DurationText.format(1588000));
  assertEquals("1h 0m 0s",DurationText.format(3600000));
 }
}
