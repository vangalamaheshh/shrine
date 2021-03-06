package net.shrine.aggregation

import net.shrine.aggregation.BasicAggregator.Valid
import net.shrine.protocol.AggregatedReadInstanceResultsResponse
import net.shrine.protocol.AggregatedReadQueryResultResponse
import net.shrine.protocol.HasQueryResults
import net.shrine.protocol.QueryResult
import net.shrine.protocol.ResultOutputType
import net.shrine.protocol.ResultOutputType.PATIENTSET
import net.shrine.protocol.ShrineResponse
import net.shrine.aggregation.StoredResultsAggregator.Aggregated

/**
 * @author clint
 * @date Nov 9, 2012
 * 
 * NB: Aggregated trait and companion object implements the typeclass pattern:
 * http://www.casualmiracles.com/2012/05/03/a-small-example-of-the-typeclass-pattern-in-scala/
 * A typeclass is used here in place of an abstract method with multiple concrete implementations,
 * or another similar strategy. -Clint
 */
abstract class StoredResultsAggregator[R <: ShrineResponse with HasQueryResults : Manifest, 
                                         AR <: ShrineResponse : Aggregated](
    shrineNetworkQueryId: Long,
    showAggregation: Boolean,
    errorMessage: Option[String] = None,
    invalidMessage: Option[String] = None) extends PackagesErrorsAggregator[R](errorMessage, invalidMessage) {

  protected def consolidateQueryResults(queryResultsFromAllValidResponses: Seq[(SpinResultEntry, Seq[QueryResult])]): Seq[QueryResult]

  protected def makeAggregatedResult(queryResults: Seq[QueryResult]): Option[QueryResult]

  import ResultOutputType._

  private val setType = Some(PATIENTSET)
  private val finishedStatusType = QueryResult.StatusType.Finished.name
  private val allowedSetTypes = ResultOutputType.values.filterNot(_.isError).toSet

  private val makeResponse = implicitly[Aggregated[AR]]

  private[aggregation] final override def makeResponse(validResponses: Seq[Valid[R]], errorResponses: Seq[QueryResult], invalidResponses: Seq[QueryResult]): ShrineResponse = {

    def isAllowedSetType(result: QueryResult) = result.resultType.map(allowedSetTypes).getOrElse(false)

    val allQueryResults = for {
      Valid(spinResult, response) <- validResponses
    } yield (spinResult, response.results)

    val queryResults = consolidateQueryResults(allQueryResults)

    //Append the aggregated response, if any
    val finalQueryResults = {
      if (showAggregation) queryResults ++ makeAggregatedResult(queryResults)
      else queryResults
    }

    makeResponse(shrineNetworkQueryId, finalQueryResults ++ errorResponses ++ invalidResponses)
  }
}

object StoredResultsAggregator {
  /**
   * @author clint
   * @date Nov 9, 2012
   */
  trait Aggregated[R] {
    def apply(shrineNetworkQueryId: Long, queryResults: Seq[QueryResult]): R
  }

  object Aggregated {
    implicit val aggregatedReadQueryResultResponseIsAggregated: Aggregated[AggregatedReadQueryResultResponse] = new Aggregated[AggregatedReadQueryResultResponse] {
      override def apply(shrineNetworkQueryId: Long, queryResults: Seq[QueryResult]) = AggregatedReadQueryResultResponse(shrineNetworkQueryId, queryResults)
    }

    implicit val aggregatedReadInstanceResultsResponseIsAggregated: Aggregated[AggregatedReadInstanceResultsResponse] = new Aggregated[AggregatedReadInstanceResultsResponse] {
      override def apply(shrineNetworkQueryId: Long, queryResults: Seq[QueryResult]) = AggregatedReadInstanceResultsResponse(shrineNetworkQueryId, queryResults)
    }
  }
}