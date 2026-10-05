package ru.billyhargrove.pimobile.core;
import org.junit.Test;import org.json.*;import static org.junit.Assert.*;
public class OrchestrationDataTest {
 @Test public void activeCountsDoNotTreatFinishedOrPausedAsRunning()throws Exception {JSONObject data=new JSONObject("{\"workflows\":[{\"id\":\"w\"}],\"agents\":[{\"status\":\"running\",\"workflowId\":\"w\"},{\"status\":\"completed\"},{\"status\":\"paused\"},{\"status\":\"queued\",\"workflowId\":\"w\"}]}");assertEquals(1,OrchestrationData.running(data));assertEquals(2,OrchestrationData.agents(data,"w").size());assertEquals("1 workflow · 4 agents · 1 running",OrchestrationData.summary(data));}
 @Test public void durationUsesRecordedCompletionAndUnknownStaysUnknown()throws Exception{JSONObject agent=new JSONObject("{\"startedAt\":1000,\"finishedAt\":62000}");assertEquals("1m 1s",OrchestrationData.duration(agent,900000));assertEquals("Unknown",OrchestrationData.label("madeup"));assertEquals("",OrchestrationData.duration(new JSONObject(),900000));assertEquals("gpt-6-sol",OrchestrationData.shortModel("openai-codex/gpt-6-sol"));}
}
