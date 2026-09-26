package app.chenadet.data

import org.junit.Assert.*
import org.junit.Test

class JsonAdapterTest {
    @Test fun nullsArraysAndNestedObjectsArePreserved() {
        val value = JsonAdapter.decode("""{"number":0,"missing":null,"list":[1,null,{"ok":true}]}""")
        assertEquals(0, value["number"])
        assertNull(value["missing"])
        val list = value["list"] as List<*>
        assertNull(list[1]); assertEquals(true, (list[2] as Map<*, *>)["ok"])
    }
    @Test fun htmlAndTruncatedDataAreRejected() {
        assertTrue(runCatching { JsonAdapter.decode("<html>Error</html>") }.isFailure)
        assertTrue(runCatching { JsonAdapter.decode("{\"temperature\":") }.isFailure)
    }
}
