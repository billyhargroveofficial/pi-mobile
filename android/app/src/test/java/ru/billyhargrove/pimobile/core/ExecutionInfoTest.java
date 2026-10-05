package ru.billyhargrove.pimobile.core;
import org.junit.Test;import static org.junit.Assert.*;import org.json.*;
public class ExecutionInfoTest {
 @Test public void runningRequestDoesNotShowNextEffortOrPretendPriorityWasAccepted()throws Exception{JSONObject config=new JSONObject("{\"model\":\"openai-codex/gpt-6-astra\",\"thinkingLevel\":\"low\",\"serviceTier\":\"fast\",\"serviceTiers\":[\"standard\",\"fast\"],\"execution\":{\"model\":\"gpt-6-astra\",\"thinkingLevel\":\"high\",\"serviceTier\":\"standard\",\"tierConfirmed\":true}}");ExecutionInfo current=new ExecutionInfo(config,true);assertEquals("high",current.effort);assertEquals("Standard",current.tierLabel(true));assertEquals("gpt-6-astra",current.model);ExecutionInfo next=new ExecutionInfo(config,false);assertEquals("low",next.effort);assertEquals("Fast",next.tierLabel(false));}
 @Test public void unconfirmedFastIsExplicit()throws Exception{JSONObject config=new JSONObject("{\"execution\":{\"model\":\"m\",\"serviceTier\":\"fast\",\"tierConfirmed\":false}}");assertEquals("Fast requested",new ExecutionInfo(config,true).tierLabel(true));assertEquals("Tier unavailable",new ExecutionInfo(null,true).tierLabel(true));}
}
