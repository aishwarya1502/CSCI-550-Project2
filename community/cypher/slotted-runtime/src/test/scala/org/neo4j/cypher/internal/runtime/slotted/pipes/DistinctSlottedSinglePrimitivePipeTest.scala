/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.slotted.pipes

import org.neo4j.cypher.internal.physicalplanning.SlotConfiguration
import org.neo4j.cypher.internal.runtime.ResourceManager
import org.neo4j.cypher.internal.runtime.interpreted.QueryStateHelper
import org.neo4j.cypher.internal.runtime.slotted.expressions.NodeFromSlot
import org.neo4j.cypher.internal.util.symbols.CTNode
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.internal.kernel.api.DefaultCloseListenable

class DistinctSlottedSinglePrimitivePipeTest extends CypherFunSuite {

  test("exhaust should close seen set") {
    val monitor = QueryStateHelper.trackClosedMonitor
    val resourceManager = new ResourceManager(monitor)
    val slots = SlotConfiguration.empty.newLong("a", nullable = false, CTNode)

    val input = FakeSlottedPipe(Seq(Map("a" -> 10)), slots)
    val pipe = DistinctSlottedSinglePrimitivePipe(input, slots, slots("a"), 0, NodeFromSlot(0))()
    // exhaust
    pipe.createResults(QueryStateHelper.emptyWithResourceManager(resourceManager)).toList
    input.wasClosed shouldBe true
    monitor.closedResources.collect { case t: DefaultCloseListenable => t } should have size (1)
  }

  test("close should close seen set") {
    val monitor = QueryStateHelper.trackClosedMonitor
    val resourceManager = new ResourceManager(monitor)
    val slots = SlotConfiguration.empty.newLong("a", nullable = false, CTNode)

    val input = FakeSlottedPipe(Seq(Map("a" -> 10)), slots)
    val pipe = DistinctSlottedSinglePrimitivePipe(input, slots, slots("a"), 0, NodeFromSlot(0))()
    val result = pipe.createResults(QueryStateHelper.emptyWithResourceManager(resourceManager))
    result.close()
    input.wasClosed shouldBe true
    monitor.closedResources.collect { case t: DefaultCloseListenable => t } should have size (1)
  }
}
