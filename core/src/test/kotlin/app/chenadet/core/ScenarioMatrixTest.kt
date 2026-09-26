package app.chenadet.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ScenarioMatrixTest {
    @Test fun allDeterministicScenarios() { assertEquals(0, CoreChecks.run()) }
}
