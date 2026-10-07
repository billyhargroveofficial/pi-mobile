package ru.billyhargrove.pimobile.core;
import org.junit.Test;import org.json.*;import static org.junit.Assert.*;
public class OrchestrationDataTest {
 @Test public void activeCountsDoNotTreatFinishedOrPausedAsRunning()throws Exception {JSONObject data=new JSONObject("{\"workflows\":[{\"id\":\"w\"}],\"agents\":[{\"status\":\"running\",\"workflowId\":\"w\"},{\"status\":\"completed\"},{\"status\":\"paused\"},{\"status\":\"queued\",\"workflowId\":\"w\"}]}");assertEquals(1,OrchestrationData.running(data));assertEquals(2,OrchestrationData.agents(data,"w").size());assertEquals("1 workflow · 4 agents · 1 running",OrchestrationData.summary(data));}
 @Test public void liveDockSeparatesWorkflowAgentsAndNeverUsesSavedOrCompletedActivity()throws Exception {
  JSONObject data=new JSONObject("{\"liveAvailable\":true,\"workflows\":[{\"id\":\"live\",\"status\":\"running\"},{\"id\":\"done\",\"status\":\"completed\"}],\"agents\":[{\"id\":\"workflow-agent\",\"workflowId\":\"live\",\"status\":\"running\"},{\"id\":\"standalone\",\"status\":\"running\"},{\"id\":\"finished\",\"status\":\"completed\"},{\"id\":\"paused\",\"status\":\"paused\"}]}");
  assertEquals(1,OrchestrationData.activeWorkflows(data).size());assertEquals("standalone",OrchestrationData.activeStandalone(data).get(0).getString("id"));
  data.put("liveAvailable",false);assertTrue(OrchestrationData.activeWorkflows(data).isEmpty());assertTrue(OrchestrationData.activeStandalone(data).isEmpty());
 }
 @Test public void metricsUseActualToolsTokensAndRecordedTime()throws Exception {
  assertEquals("",OrchestrationData.metrics(new JSONObject(),900000));
  assertEquals("7 tools · 1200 tokens · 1m 1s",OrchestrationData.metrics(new JSONObject("{\"toolCalls\":7,\"outputTokens\":1200,\"startedAt\":1000,\"finishedAt\":62000}"),900000));
 }
 @Test public void durationUsesRecordedCompletionAndUnknownStaysUnknown()throws Exception{JSONObject agent=new JSONObject("{\"startedAt\":1000,\"finishedAt\":62000}");assertEquals("1m 1s",OrchestrationData.duration(agent,900000));assertEquals("Unknown",OrchestrationData.label("madeup"));assertEquals("",OrchestrationData.duration(new JSONObject(),900000));assertEquals("gpt-6-sol",OrchestrationData.shortModel("openai-codex/gpt-6-sol"));}
}
