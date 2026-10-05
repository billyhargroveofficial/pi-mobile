package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SessionGroupingTest {

    private static Catalog catalog() {
        List<Workspace> workspaces = Arrays.asList(
                new Workspace("w1", "pi-mobile", "/Users/billy/repos/pi-mobile"),
                new Workspace("w2", "site_rebuild", "/Users/billy/site"));
        List<Session> sessions = Arrays.asList(
                new Session("s1", "Ожидает", "/Users/billy/repos/pi-mobile", "w1", "t1", true, SessionStatus.IDLE, "m"),
                new Session("s2", "Работает", "/Users/billy/repos/pi-mobile", "w1", "t2", true, SessionStatus.RUNNING, "m"),
                new Session("s3", "Вне Orca", "/tmp/other", "", "", false, SessionStatus.IDLE, ""),
                new Session("s4", "Вне Orca 2", "/tmp/other", null, null, false, SessionStatus.OFFLINE, ""),
                new Session("s5", "Чужое пространство", "/x", "w-unknown", "", false, SessionStatus.UNKNOWN, ""));
        List<TerminalInfo> terminals = Collections.singletonList(
                new TerminalInfo("t9", "shell", "w2", "claude", false));
        return new Catalog(workspaces, sessions, terminals);
    }

    @Test
    public void producesHeadersThenRows() {
        List<CatalogRow> rows = SessionGrouping.build(catalog());
        List<String> headers = new ArrayList<>();
        for (CatalogRow row : rows) {
            if (row.isHeader()) {
                headers.add(row.title());
            }
        }
        assertEquals(Arrays.asList("pi-mobile", "site_rebuild", "other", "x"), headers);
    }

    @Test
    public void runningSessionsComeFirstInsideAGroup() {
        List<CatalogRow> rows = SessionGrouping.build(catalog());
        int firstHeader = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).isHeader()) {
                firstHeader = i;
                break;
            }
        }
        assertEquals("Работает", rows.get(firstHeader + 1).title());
        assertEquals("Ожидает", rows.get(firstHeader + 2).title());
    }

    @Test
    public void sessionsOutsideOrcaGroupByCwd() {
        List<CatalogRow> rows = SessionGrouping.build(catalog());
        int otherHeader = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).isHeader() && "other".equals(rows.get(i).title())) {
                otherHeader = i;
            }
        }
        assertTrue(otherHeader > 0);
        assertEquals("Вне Orca", rows.get(otherHeader).subtitle());
        assertEquals(2, rows.get(otherHeader).count());
        // Unknown workspace id also lands in a cwd group, here "x".
        boolean unknownGroupSeen = false;
        for (CatalogRow row : rows) {
            if (row.isHeader() && "x".equals(row.title())) {
                unknownGroupSeen = true;
            }
        }
        assertTrue(unknownGroupSeen);
    }

    @Test
    public void terminalsAreReadOnlyAndMarked() {
        List<CatalogRow> rows = SessionGrouping.build(catalog());
        CatalogRow terminal = null;
        for (CatalogRow row : rows) {
            if (row.kind() == CatalogRow.Kind.TERMINAL) {
                terminal = row;
            }
        }
        assertTrue(terminal != null);
        assertTrue(terminal.readOnly());
        assertEquals("требуется расширение", terminal.badge());
        assertEquals("claude", terminal.subtitle());
        assertFalse(terminal.connected());
    }

    @Test
    public void emptyWorkspacesRemainVisible() {
        Catalog catalog = new Catalog(
                Arrays.asList(new Workspace("w1", "empty", "/e"), new Workspace("w2", "full", "/f")),
                Collections.singletonList(new Session("s1", "x", "/f", "w2", "", true, SessionStatus.IDLE, "")),
                null);
        List<CatalogRow> rows = SessionGrouping.build(catalog);
        assertEquals(3, rows.size());
        assertEquals("empty", rows.get(0).title());
        assertEquals(0, rows.get(0).count());
        assertEquals("full", rows.get(1).title());
        assertEquals("x", rows.get(2).title());
    }

    @Test
    public void nullCatalogIsSafe() {
        assertTrue(SessionGrouping.build(null).isEmpty());
        assertTrue(SessionGrouping.build(Catalog.empty()).isEmpty());
    }

    @Test
    public void rowKeysAreStable() {
        List<CatalogRow> rows = SessionGrouping.build(catalog());
        List<CatalogRow> again = SessionGrouping.build(catalog());
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(rows.get(i).key(), again.get(i).key());
        }
    }
}
