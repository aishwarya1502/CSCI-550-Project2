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
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import org.neo4j.cypher.internal.RecoverableCypherError
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsOnErrorBehaviour
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsOnErrorBehaviour.OnErrorBreak
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsOnErrorBehaviour.OnErrorContinue
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsOnErrorBehaviour.OnErrorFail
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.EntityTransformer
import org.neo4j.cypher.internal.runtime.QueryStatistics
import org.neo4j.cypher.internal.runtime.QueryTransactionalContext
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.TransactionPipeWrapper.CypherRowEntityTransformer
import org.neo4j.cypher.internal.runtime.interpreted.pipes.TransactionPipeWrapper.assertTransactionStateIsEmpty
import org.neo4j.cypher.internal.runtime.interpreted.pipes.TransactionPipeWrapper.commitTransactionWithStatistics
import org.neo4j.cypher.internal.runtime.interpreted.pipes.TransactionPipeWrapper.logError
import org.neo4j.exceptions.InternalException
import org.neo4j.kernel.impl.util.collection.EagerBuffer
import org.neo4j.kernel.impl.util.collection.EagerBuffer.createEagerBuffer
import org.neo4j.memory.MemoryTracker

import scala.util.Try
import scala.util.control.NonFatal

/**
 * Wraps a pipe to execute that pipe in separate transactions.
 * 
 * NOTE! Implementations might keep state that is not safe to re-use between queries. Create a new instance for each query.
 */
trait TransactionPipeWrapper {
  def inner: Pipe

  /**
   * Consumes the inner pipe in a new transaction and discard the resulting rows.
   * 
   * @param state query state
   * @param outerRows outer rows, will not be closed as part of this call
   */
  def consume(state: QueryState, outerRows: EagerBuffer[CypherRow]): TransactionStatus = {
    processBatch(state, outerRows)(_ => ())
  }

  /**
   * Consumes the inner pipe in a new transaction and returns the inner rows.
   *
   * @param state query state
   * @param outerRows outer rows, will not be closed as part of this call
   * @param memoryTracker memory tracker for tracking the buffered resulting rows
   */
  def createResults(
    state: QueryState,
    outerRows: EagerBuffer[CypherRow],
    memoryTracker: MemoryTracker
  ): TransactionResult = {
    val entityTransformer = new CypherRowEntityTransformer(state.query.entityTransformer)
    val innerResult = createEagerBuffer[CypherRow](memoryTracker, math.min(outerRows.size(), 1024).toInt)

    val status = processBatch(state, outerRows) { innerRow =>
      // Row based caching relies on the transaction state to avoid stale reads (see AbstractCachedProperty.apply).
      // Since we do not share the transaction state we must clear the cached properties.
      innerRow.invalidateCachedProperties()
      innerResult.add(entityTransformer.copyWithEntityWrappingValuesRebound(innerRow))
    }

    status match {
      case commit: Commit => TransactionResult(commit, Some(innerResult))
      case other =>
        innerResult.close()
        TransactionResult(other, None)
    }
  }

  protected def processBatch(
    state: QueryState,
    outerRows: EagerBuffer[CypherRow] // Should not be closed
  )(f: CypherRow => Unit): TransactionStatus

  /**
   * Evaluates inner pipe in a new transaction.
   * 
   * @param state query state
   * @param outerRows buffered outer rows, will not be closed by this method
   * @param f function to apply to inner rows
   */
  protected def createInnerResultsInNewTransaction(
    state: QueryState,
    outerRows: EagerBuffer[CypherRow] // Should not be closed
  )(f: CypherRow => Unit): TransactionStatus = {

    // Ensure that no write happens before a 'CALL { ... } IN TRANSACTIONS'
    assertTransactionStateIsEmpty(state)

    // beginTx()
    val stateWithNewTransaction = state.withNewTransaction()
    state.query.addStatistics(QueryStatistics(transactionsStarted = 1))
    val innerTxContext = stateWithNewTransaction.query.transactionalContext
    val transactionId = innerTxContext.userTransactionId
    val entityTransformer = new CypherRowEntityTransformer(stateWithNewTransaction.query.entityTransformer)

    var innerIterator: ClosingIterator[CypherRow] = null
    try {
      val batchIterator = outerRows.iterator()
      while (batchIterator.hasNext) {
        val outerRow = batchIterator.next()

        outerRow.invalidateCachedProperties()

        val reboundRow = entityTransformer.copyWithEntityWrappingValuesRebound(outerRow)
        val innerState = stateWithNewTransaction.withInitialContext(reboundRow)

        innerIterator = inner.createResults(innerState)
        innerIterator.foreach(f.apply) // Consume result before commit
      }

      state.query.addStatistics(stateWithNewTransaction.getStatistics)
      commitTransactionWithStatistics(innerTxContext, state)
      Commit(transactionId)
    } catch {
      case RecoverableCypherError(e) =>
        logError(state, transactionId, e)

        Try(Option(innerIterator).foreach(_.close()))
          .failed
          .foreach(e.addSuppressed)

        try {
          state.query.addStatistics(QueryStatistics(transactionsRolledBack = 1))
          innerTxContext.rollback()
        } catch {
          case NonFatal(rollbackException) =>
            e.addSuppressed(rollbackException)
            throw e
        }
        Rollback(transactionId, e)
    } finally {
      innerTxContext.close()
      stateWithNewTransaction.close()
    }
  }
}

class OnErrorContinueTxPipe(val inner: Pipe) extends TransactionPipeWrapper {

  override def processBatch(
    state: QueryState,
    outerRows: EagerBuffer[CypherRow]
  )(f: CypherRow => Unit): TransactionStatus = {
    createInnerResultsInNewTransaction(state, outerRows)(f)
  }
}

// NOTE! Keeps state that is not safe to re-use between queries. Create a new instance for each query.
class OnErrorBreakTxPipe(val inner: Pipe) extends TransactionPipeWrapper {
  private[this] var break: Boolean = false

  override def processBatch(
    state: QueryState,
    outerRows: EagerBuffer[CypherRow]
  )(f: CypherRow => Unit): TransactionStatus = {
    if (break) {
      NotRun
    } else {
      createInnerResultsInNewTransaction(state, outerRows)(f) match {
        case commit: Commit => commit
        case rollback: Rollback =>
          break = true
          rollback
        case other => throw new IllegalStateException(s"Unexpected transaction status $other")
      }
    }
  }
}

class OnErrorFailTxPipe(val inner: Pipe) extends TransactionPipeWrapper {

  override def processBatch(
    state: QueryState,
    outerRows: EagerBuffer[CypherRow]
  )(f: CypherRow => Unit): TransactionStatus = {
    createInnerResultsInNewTransaction(state, outerRows)(f) match {
      case commit: Commit     => commit
      case rollback: Rollback => throw rollback.failure
      case other              => throw new IllegalStateException(s"Unexpected transaction status $other")
    }
  }
}

sealed trait TransactionStatus
case class Commit(transactionId: String) extends TransactionStatus
case class Rollback(transactionId: String, failure: Throwable) extends TransactionStatus
case object NotRun extends TransactionStatus

case class TransactionResult(status: TransactionStatus, committedResults: Option[EagerBuffer[CypherRow]])

object TransactionPipeWrapper {

  /**
   * Wrap a pipeline to run in new transactions based on the specified behaviour.
   * 
   * NOTE! Implementations might keep state that is not safe to re-use between queries. Create a new instance for each query.
   */
  def apply(error: InTransactionsOnErrorBehaviour, inner: Pipe): TransactionPipeWrapper = {
    error match {
      case OnErrorContinue => new OnErrorContinueTxPipe(inner)
      case OnErrorBreak    => new OnErrorBreakTxPipe(inner)
      case OnErrorFail     => new OnErrorFailTxPipe(inner)
      case other           => throw new UnsupportedOperationException(s"Unsupported error behaviour $other")
    }
  }

  /**
   * Recursively finds entity wrappers and rebinds the entities to the current transaction
   */
  // TODO: Remove rebinding here, and transform wrappers to Reference:s
  // Currently, replacing e.g. NodeEntityWrappingNodeValue with NodeReference causes failures downstream.
  // We can for example end up in PathValueBuilder, which assumes that we have NodeValue and not NodeReference.
  // We can also still get entity values with transaction references streaming in and out of procedures.
  // Always copying the row should not be necessary. We could optimize this by first doing a dry-run to detect if anything actually needs to be rebound.
  class CypherRowEntityTransformer(entityTransformer: EntityTransformer) {

    def copyWithEntityWrappingValuesRebound(row: CypherRow): CypherRow =
      row.copyMapped(entityTransformer.rebindEntityWrappingValue)
  }

  def evaluateBatchSize(batchSize: Expression, state: QueryState): Long = {
    PipeHelper.evaluateStaticLongOrThrow(batchSize, _ > 0, state, "OF ... ROWS", " Must be a positive integer.")
  }

  def assertTransactionStateIsEmpty(state: QueryState): Unit = {
    if (state.query.transactionalContext.dataRead.transactionStateHasChanges)
      throw new InternalException("Expected transaction state to be empty when calling transactional subquery.")
  }

  private def commitTransactionWithStatistics(
    innerTxContext: QueryTransactionalContext,
    outerQueryState: QueryState
  ): Unit = {
    innerTxContext.commitTransaction()

    val executionStatistics = QueryStatistics(transactionsCommitted = 1)
    outerQueryState.query.addStatistics(executionStatistics)
  }

  private def logError(state: QueryState, innerTxId: String, t: Throwable): Unit = {
    val outerTxId = state.query.transactionalContext.userTransactionId
    val log = state.query.logProvider.getLog(getClass)
    log.info(s"Recover error in inner transaction $innerTxId (outer transaction $outerTxId)", t)
  }
}
