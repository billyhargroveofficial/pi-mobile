package ru.billyhargrove.pimobile.core

class Workspace(id: String?, name: String?, path: String?) {
    private val id = id.orEmpty()
    private val name = name.orEmpty()
    private val path = path.orEmpty()
    fun id() = id
    fun name() = name
    fun path() = path
    fun displayName() = name.ifEmpty { path.ifEmpty { id.ifEmpty { "Без имени" } } }
    override fun equals(other: Any?): Boolean = other is Workspace &&
        id == other.id && name == other.name && path == other.path
    override fun hashCode() = java.util.Objects.hash(id, name, path)
    override fun toString() = "Workspace{$id, $name}"
}
