package ru.billyhargrove.pimobile.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class DurationTextTest {
 @Test public void durationUsesOnlyNeededUnits(){
  assertEquals("0с",DurationText.format(-1));
  assertEquals("59с",DurationText.format(59999));
  assertEquals("1м 0с",DurationText.format(60000));
  assertEquals("26м 28с",DurationText.format(1588000));
  assertEquals("1ч 0м 0с",DurationText.format(3600000));
 }
}
