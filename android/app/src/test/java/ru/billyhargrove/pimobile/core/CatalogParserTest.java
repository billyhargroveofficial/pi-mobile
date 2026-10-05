package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class CatalogParserTest {

    private static final String CATALOG = "{"
            + "\"type\":\"catalog\","
            + "\"workspaces\":[{\"id\":\"w1\",\"name\":\"pi-mobile\",\"path\":\"/Users/billy/repos/pi-mobile\"}],"
            + "\"sessions\":["
            + "  {\"id\":\"s1\",\"title\":\"Сборка\",\"cwd\":\"/Users/billy/repos/pi-mobile\",\"workspaceId\":\"w1\","
            + "   \"terminalId\":\"t1\",\"connected\":true,\"status\":\"running\",\"model\":\"deepseek-pro\"},"
            + "  {\"id\":\"s2\",\"title\":\"Без Orca\",\"cwd\":\"/tmp/other\",\"workspaceId\":null,"
            + "   \"connected\":false,\"status\":\"idle\"},"
            + "  {\"title\":\"без идентификатора\"}"
            + "],"
            + "\"terminals\":[{\"id\":\"t9\",\"title\":\"shell\",\"workspaceId\":\"w1\",\"agent\":\"claude\",\"connected\":false}]"
            + "}";

    @Test
    public void parsesTheFullCatalog() {
        Catalog catalog = CatalogParser.parse(CATALOG);
        assertNotNull(catalog);
        assertEquals(1, catalog.workspaces().size());
        assertEquals(2, catalog.sessions().size());
        assertEquals(1, catalog.terminals().size());
        assertEquals("pi-mobile", catalog.workspaces().get(0).displayName());

        Session first = catalog.sessions().get(0);
        assertEquals("s1", first.id());
        assertEquals(SessionStatus.RUNNING, first.status());
        assertTrue(first.connected());
        assertEquals("deepseek-pro", first.model());

        Session second = catalog.sessions().get(1);
        assertEquals(SessionStatus.IDLE, second.status());
        assertTrue(!second.hasWorkspace());

        assertEquals("t9", catalog.terminals().get(0).id());
        assertEquals("claude", catalog.terminals().get(0).agent());
    }

    @Test
    public void rejectsForeignOrBrokenPayloads() {
        assertNull(CatalogParser.parse((String) null));
        assertNull(CatalogParser.parse(""));
        assertNull(CatalogParser.parse("не json"));
        assertNull(CatalogParser.parse("{\"type\":\"snapshot\"}"));
        assertNull(CatalogParser.parse("{\"type\":\"ack\"}"));
    }

    @Test
    public void emptyArraysProduceAnEmptyCatalog() {
        Catalog catalog = CatalogParser.parse("{\"type\":\"catalog\"}");
        assertNotNull(catalog);
        assertEquals(0, catalog.sessions().size());
        assertEquals(0, catalog.terminals().size());
        assertNull(catalog.findSession("missing"));
    }

    @Test
    public void findSessionLooksUpById() {
        Catalog catalog = CatalogParser.parse(CATALOG);
        assertNotNull(catalog.findSession("s1"));
        assertEquals("Сборка", catalog.findSession("s1").displayTitle());
    }

    @Test
    public void unknownStatusBecomesUnknown() {
        Catalog catalog = CatalogParser.parse("{\"type\":\"catalog\",\"sessions\":[{\"id\":\"x\",\"status\":\"weird\"}]}");
        assertEquals(SessionStatus.UNKNOWN, catalog.sessions().get(0).status());
        assertEquals(SessionStatus.UNKNOWN, SessionStatus.parse(null));
    }

    @Test
    public void displayTitleFallsBackToTheId() {
        Catalog catalog = CatalogParser.parse("{\"type\":\"catalog\",\"sessions\":[{\"id\":\"solo\"}]}");
        assertEquals("solo", catalog.sessions().get(0).displayTitle());
    }

    @Test
    public void groupsUnknownWorkspaceSessionsByCwd() {
        Catalog catalog = CatalogParser.parse(CATALOG);
        List<CatalogRow> rows = SessionGrouping.build(catalog);
        boolean hasWorkspaceHeader = false;
        boolean hasCwdHeader = false;
        for (CatalogRow row : rows) {
            if (row.isHeader() && "pi-mobile".equals(row.title())) {
                hasWorkspaceHeader = true;
            }
            if (row.isHeader() && "other".equals(row.title())) {
                hasCwdHeader = true;
            }
        }
        assertTrue(hasWorkspaceHeader);
        assertTrue(hasCwdHeader);
    }
}
