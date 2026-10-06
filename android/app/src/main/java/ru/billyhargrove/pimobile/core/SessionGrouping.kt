package ru.billyhargrove.pimobile.core

/** Gateway workspace order, including empty workspaces; unassigned sessions group by cwd. */
object SessionGrouping {
    private class Group(val key: String, val title: String, val subtitle: String) {
        val sessions = mutableListOf<Session>()
        val terminals = mutableListOf<TerminalInfo>()
    }
    @JvmStatic fun build(catalog: Catalog?): List<CatalogRow> {
        if (catalog == null) return emptyList()
        val groups = linkedMapOf<String, Group>()
        val workspaceKeys = linkedMapOf<String, String>()
        val order = mutableListOf<String>()
        for (workspace in catalog.workspaces()) {
            val key = "ws:${workspace.id()}"
            order.add(key); groups[key] = Group(key, workspace.displayName(), workspace.path()); workspaceKeys[workspace.id()] = key
        }
        for (session in catalog.sessions()) {
            val workspace = if (session.hasWorkspace()) workspaceKeys[session.workspaceId()] else null
            val key = workspace ?: "cwd:${session.cwd().ifEmpty { "-" }}"
            if (key !in groups) {
                order.add(key)
                val cwd = session.cwd()
                val slash = cwd.lastIndexOf('/')
                val label = if (cwd.isEmpty()) "Без рабочей папки" else if (slash >= 0 && slash + 1 < cwd.length) cwd.substring(slash + 1) else cwd
                groups[key] = Group(key, label, "Вне Orca")
            }
            groups.getValue(key).sessions.add(session)
        }
        for (terminal in catalog.terminals()) {
            val key = workspaceKeys[terminal.workspaceId()] ?: "ws-other"
            if (key !in groups) { order.add(key); groups[key] = Group(key, "Прочие терминалы", "Вне Orca") }
            groups.getValue(key).terminals.add(terminal)
        }
        val sorter = Comparator<Session> { a, b ->
            val rank = a.sortRank().compareTo(b.sortRank())
            val title = a.displayTitle().compareTo(b.displayTitle(), ignoreCase = true)
            if (rank != 0) rank else if (title != 0) title else a.id().compareTo(b.id())
        }
        return buildList {
            for (key in order) {
                val group = groups.getValue(key)
                add(CatalogRow.header(key, group.title, group.subtitle, group.sessions.size + group.terminals.size))
                group.sessions.sortedWith(sorter).forEach { add(CatalogRow.session(it)) }
                group.terminals.forEach { add(CatalogRow.terminal(it)) }
            }
        }
    }
}
