package ru.billyhargrove.pimobile.store

import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.EndpointPolicy

class ConnectionProfilesTest {
    private class Port : ConnectionProfiles.Port {
        var raw = ConnectionProfiles.Raw(null, EndpointPolicy.DEFAULT_BASE_URL, null)
        var writes = 0; var failWrite = false; var failEncrypt = false; var cipherSequence = 0
        val secrets = mutableMapOf<String, String>()
        override fun read() = raw
        override fun write(raw: ConnectionProfiles.Raw) { if (failWrite) error("Disk unavailable"); this.raw = raw; writes++ }
        override fun encrypt(token: String): String? = if (failEncrypt) null else "cipher-${cipherSequence++}".also { secrets[it] = token }
        override fun decrypt(envelope: String) = secrets[envelope]
    }
    private fun owner(port: Port): ConnectionProfiles { var sequence = port.secrets.size; return ConnectionProfiles(port, true) { "id-${sequence++}" } }
    private fun rejected(action: () -> Unit) { try { action(); fail("Must reject") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }

    @Test fun legacyMigrationIsLazyPreservesCipherAndOutboxNamespace() {
        val port = Port(); val cipher = port.encrypt("first-secret")!!
        port.raw = ConnectionProfiles.Raw(null, "https://FIRST.example:443/", cipher)
        val store = owner(port)
        assertEquals("legacy", store.activeId()); assertEquals("first-secret", store.token()); assertEquals(0, port.writes)
        store.saveAndSelect("first.example", "Desktop", "")
        assertEquals("https://FIRST.example:443/", store.baseUrl()); assertEquals(cipher, port.raw.envelope)
        assertEquals("legacy", store.profiles().single().id); assertFalse(port.raw.profiles!!.contains("first-secret"))
    }
    @Test fun hostsSurviveRestartAndKeepIndependentTokens() {
        val port = Port(); val store = owner(port)
        val a = store.saveAndSelect("first.example", "Desktop", "first-secret")
        val b = store.saveAndSelect("second.example:8443/gw", "Laptop", "second-secret")
        assertEquals("second-secret", store.token()); assertEquals(2, store.profiles().size)
        val reopened = owner(port); reopened.select(a.id)
        assertEquals("https://first.example", reopened.baseUrl()); assertEquals("first-secret", reopened.token())
        reopened.select(b.id); assertEquals("second-secret", reopened.token())
        assertFalse(port.raw.profiles!!.contains("first-secret")); assertFalse(port.raw.profiles!!.contains("second-secret"))
    }
    @Test fun canonicalAddressDeduplicatesButPathAndPortAreDifferentComputers() {
        val port = Port(); val store = owner(port)
        val first = store.saveAndSelect("https://FIRST.example:443/", "First", "a")
        val renamed = store.saveAndSelect("first.example", "Renamed", "")
        assertEquals(first.id, renamed.id); assertEquals("Renamed", renamed.name); assertEquals(1, store.profiles().size)
        store.saveAndSelect("first.example/gw", "Gateway", "b")
        store.saveAndSelect("first.example:8443", "Port", "c")
        assertEquals(3, store.profiles().size)
    }
    @Test fun blankTokenCannotBorrowThePreviousComputerCredential() {
        val port = Port(); val store = owner(port); val first = store.saveAndSelect("first.example", "First", "a")
        val before = port.raw
        assertFalse(store.hasTokenFor("second.example")); rejected { store.saveAndSelect("second.example", "Second", "") }
        assertEquals(before, port.raw); assertEquals(first.id, store.activeId()); assertEquals("a", store.token())
    }
    @Test fun editingInactiveComputerRetainsItsOwnTokenNotTheActiveToken() {
        val port = Port(); val store = owner(port); val a = store.saveAndSelect("a.example", "A", "a-secret")
        store.saveAndSelect("b.example", "B", "b-secret")
        store.saveAndSelect("a.example/", "Updated", "")
        assertEquals(a.id, store.activeId()); assertEquals("a-secret", store.token())
        store.saveAndSelect("a.example", "", "new-a"); assertEquals("Updated", store.profiles().first().name); assertEquals("new-a", store.token())
    }
    @Test fun encryptionAndCommitFailureKeepTheWorkingComputerSelected() {
        val port = Port(); val store = owner(port); store.saveAndSelect("a.example", "A", "a")
        val before = port.raw
        port.failEncrypt = true; rejected { store.saveAndSelect("b.example", "B", "b") }; assertEquals(before, port.raw)
        port.failEncrypt = false; port.failWrite = true; rejected { store.saveAndSelect("b.example", "B", "b") }
        assertEquals(before, port.raw); assertEquals("a", store.token()); assertEquals(1, store.profiles().size)
    }
    @Test fun corruptAndFutureMetadataFailClosedWithoutReplacingStoredData() {
        for (blob in listOf("{broken", "{\"version\":2,\"computers\":[]}", "{\"version\":1,\"activeId\":\"missing\",\"computers\":[]}")) {
            val port = Port(); port.raw = ConnectionProfiles.Raw(blob, "a.example", port.encrypt("secret"))
            val before = port.raw; val store = owner(port)
            assertTrue(store.profiles().isEmpty()); assertFalse(store.hasToken()); assertEquals("", store.token())
            rejected { store.saveAndSelect("b.example", "B", "b") }; assertEquals(before, port.raw); assertEquals(0, port.writes)
        }
    }
    @Test fun unreadableCipherRetiresOnlyThatComputer() {
        val port = Port(); val store = owner(port); val a = store.saveAndSelect("a.example", "A", "a")
        val bad = port.raw.envelope!!; val b = store.saveAndSelect("b.example", "B", "b"); port.secrets.remove(bad)
        rejected { store.select(a.id) }
        assertEquals(b.id, store.activeId()); assertEquals("b", store.token()); assertFalse(store.profiles().first().hasToken)
        store.saveAndSelect("a.example", "A", "repaired"); assertEquals("repaired", store.token())
        store.select(b.id); assertEquals("b", store.token())
    }
    @Test fun clearingOrForgettingOneCredentialNeverInvalidatesOthers() {
        val port = Port(); val store = owner(port); val a = store.saveAndSelect("a.example", "A", "a")
        val b = store.saveAndSelect("b.example", "B", "b"); store.clearToken()
        assertFalse(store.hasToken()); store.select(a.id); assertEquals("a", store.token())
        store.forget(b.id); assertEquals(a.id, store.activeId()); assertEquals("a", store.token())
        store.saveAndSelect("b.example", "B", "b2"); store.forget(store.activeId()!!)
        assertNull(store.activeId()); assertEquals("", store.token()); assertFalse(store.hasToken()); assertEquals(1, store.profiles().size)
        store.select(a.id); assertEquals("a", store.token())
    }
    @Test fun legacySetupChangingAddressClearsActiveCredentialInsteadOfReusingIt() {
        val port = Port(); val store = owner(port); val a = store.saveAndSelect("a.example", "A", "a")
        store.setBaseUrl("https://new.example/"); assertNull(store.activeId()); assertEquals("", store.token())
        assertEquals("https://new.example/", store.baseUrl()); assertEquals(1, store.profiles().size)
        store.setBaseUrl("a.example"); assertEquals(a.id, store.activeId()); assertEquals("a", store.token())
    }
    @Test fun boundsRejectWithoutChangingDurableState() {
        val port = Port(); val store = owner(port)
        rejected { store.saveAndSelect("a.example", "x".repeat(81), "a") }
        rejected { store.saveAndSelect("a.example", "A", "x".repeat(16385)) }
        rejected { store.saveAndSelect("https://a.example:65536", "A", "a") }
        rejected { store.saveAndSelect("https://a.example/" + "x".repeat(4096), "A", "a") }
        repeat(ConnectionProfiles.MAX_PROFILES) { store.saveAndSelect("h$it.example", "H $it", "t$it") }
        val before = port.raw; rejected { store.saveAndSelect("extra.example", "Extra", "extra") }; assertEquals(before, port.raw)
        store.saveAndSelect("h0.example", "Edited at limit", ""); assertEquals(32, store.profiles().size)
    }
    @Test fun separateRepositoryInstancesObserveTheAtomicPreferenceChange() {
        val port = Port(); val first = owner(port); first.saveAndSelect("a.example", "A", "a")
        val second = owner(port); assertEquals(1, second.profiles().size)
        val b = first.saveAndSelect("b.example", "B", "b")
        assertEquals(2, second.profiles().size); assertEquals(b.id, second.activeId()); assertEquals("b", second.token())
    }
}
