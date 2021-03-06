package net.shrine.utilities.scanner

import net.shrine.ont.data.OntologyDAO
import net.shrine.config.AdapterMappingsSource
import net.shrine.client.ShrineClient
import net.shrine.protocol.ResultOutputType
import net.shrine.protocol.query.QueryDefinition
import net.shrine.protocol.query.Term
import net.shrine.protocol.AggregatedRunQueryResponse
import net.shrine.protocol.QueryResult
import scala.concurrent.duration.Duration
import net.shrine.util.Loggable
import net.shrine.util.Util.time
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.collection.GenTraversableOnce
import scala.concurrent.Await
import net.shrine.utilities.scanner.components.HasScannerConfig

/**
 * @author clint
 * @date Mar 5, 2013
 */
trait Scanner extends Loggable {

  //All of our dependencies are specified as abstract vals
  val reScanTimeout: Duration
  val adapterMappingsSource: AdapterMappingsSource
  val ontologyDao: OntologyDAO
  val client: ScannerClient

  def scan(): ScanResults = doScan()
  
  protected def doScan(): ScanResults = {
    info("Shrine Scanner starting")

    val mappedNetworkTerms = adapterMappingsSource.load.networkTerms

    def allShrineOntologyTerms = ontologyDao.ontologyEntries.map(_.path).toSet

    val termsExpectedToBeUnmapped = allShrineOntologyTerms -- mappedNetworkTerms

    info(s"We expect ${mappedNetworkTerms.size} to be mapped, and ${termsExpectedToBeUnmapped.size} to be unmapped.")

    doScan(mappedNetworkTerms, termsExpectedToBeUnmapped)
  }

  import scala.concurrent.duration._

  //TODO: Evaluate, possibly don't block?
  private val howLongToWaitForAllResults = 1 day

  private def doScan(mappedNetworkTerms: Set[String], termsExpectedToBeUnmapped: Set[String]): ScanResults = {

    def queryFor(t: String) = Await.result(client.query(t), howLongToWaitForAllResults)
    
    val resultsForMappedTerms = mappedNetworkTerms.map(queryFor)

    val resultsForUnMappedTerms = termsExpectedToBeUnmapped.map(queryFor)

    val (finishedAndShouldHaveBeenMapped, didntFinishAndShouldHaveBeenMapped) = resultsForMappedTerms.partition(_.status.isDone)

    val (finishedAndShouldNotHaveBeenMapped, didntFinishAndShouldNotHaveBeenMapped) = resultsForUnMappedTerms.partition(_.status.isDone)

    //Terms that we expected to BE mapped, but were NOT mapped
    val shouldHaveBeenMapped = finishedAndShouldHaveBeenMapped.filter(_.status.isError)

    //Terms that we expected to NOT be mapped, but ARE mapped
    val shouldNotHaveBeenMapped = finishedAndShouldNotHaveBeenMapped.filterNot(_.status.isError)

    val reScanResults = reScan(didntFinishAndShouldHaveBeenMapped, didntFinishAndShouldNotHaveBeenMapped)

    val finalSouldHaveBeenMappedSet = toTermSet(shouldHaveBeenMapped) ++ reScanResults.shouldHaveBeenMapped

    val finalSouldNotHaveBeenMappedSet = toTermSet(shouldNotHaveBeenMapped) ++ reScanResults.shouldNotHaveBeenMapped

    //Split query results into those that completed on the first try, and those that didn't
    ScanResults(finalSouldHaveBeenMappedSet, finalSouldNotHaveBeenMappedSet, reScanResults.neverFinished)
  }

  private def reScan(neverFinishedShouldHaveBeenMapped: Set[TermResult], neverFinishedShouldNotHaveBeenMapped: Set[TermResult]): ScanResults = {
    if (neverFinishedShouldHaveBeenMapped.isEmpty && neverFinishedShouldNotHaveBeenMapped.isEmpty) { ScanResults.empty }
    else {
      val total = neverFinishedShouldNotHaveBeenMapped.size + neverFinishedShouldNotHaveBeenMapped.size

      info(s"Sleeping for ${reScanTimeout} before retreiving results for $total incomplete queries...")

      Thread.sleep(reScanTimeout.toMillis)

      def retrieve(termResult: TermResult) = Await.result(client.retrieveResults(termResult), howLongToWaitForAllResults)
      
      val neverFinishedShouldHaveBeenMappedRetries = neverFinishedShouldHaveBeenMapped.map(retrieve)

      val neverFinishedShouldNotHaveBeenMappedRetries = neverFinishedShouldNotHaveBeenMapped.map(retrieve)

      val (doneShouldHaveBeenMapped, stillNotFinishedShouldHaveBeenMapped) = neverFinishedShouldHaveBeenMappedRetries.partition(_.status.isDone)

      val (doneShouldNotHaveBeenMapped, stillNotFinishedShouldNotHaveBeenMapped) = neverFinishedShouldNotHaveBeenMappedRetries.partition(_.status.isDone)

      val shouldHaveBeenMapped = doneShouldHaveBeenMapped.filter(_.status.isError)

      val shouldNotHaveBeenMapped = doneShouldNotHaveBeenMapped.filterNot(_.status.isError)

      val stillNotFinished = stillNotFinishedShouldHaveBeenMapped ++ stillNotFinishedShouldNotHaveBeenMapped

      ScanResults(toTermSet(shouldHaveBeenMapped), toTermSet(shouldNotHaveBeenMapped), toTermSet(stillNotFinished))
    }
  }

  private def toTermSet(results: Set[TermResult]): Set[String] = results.map(_.term)
}

object Scanner {
  final object QueryDefaults {
    val topicId = "foo" //???
    val outputTypes = Set(ResultOutputType.PATIENT_COUNT_XML)
  }
}