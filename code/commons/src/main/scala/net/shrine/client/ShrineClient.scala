package net.shrine.client

import scala.collection.JavaConverters.asScalaSetConverter
import scala.xml.NodeSeq

import net.shrine.protocol.AggregatedReadInstanceResultsResponse
import net.shrine.protocol.AggregatedReadQueryResultResponse
import net.shrine.protocol.AggregatedRunQueryResponse
import net.shrine.protocol.DeleteQueryResponse
import net.shrine.protocol.ReadApprovedQueryTopicsResponse
import net.shrine.protocol.ReadPdoResponse
import net.shrine.protocol.ReadPreviousQueriesResponse
import net.shrine.protocol.ReadQueryDefinitionResponse
import net.shrine.protocol.ReadQueryInstancesResponse
import net.shrine.protocol.RenameQueryResponse
import net.shrine.protocol.ResultOutputType
import net.shrine.protocol.query.QueryDefinition

/**
 *
 * @author Clint Gilbert
 * @date Sep 14, 2011
 *
 * @link http://cbmi.med.harvard.edu
 *
 */
trait ShrineClient {
  def readApprovedQueryTopics(userId: String, shouldBroadcast: Boolean): ReadApprovedQueryTopicsResponse

  def readPreviousQueries(userId: String, fetchSize: Int, shouldBroadcast: Boolean): ReadPreviousQueriesResponse

  def runQuery(topicId: String, outputTypes: Set[ResultOutputType], queryDefinition: QueryDefinition, shouldBroadcast: Boolean): AggregatedRunQueryResponse
  
  def readQueryInstances(queryId: Long, shouldBroadcast: Boolean): ReadQueryInstancesResponse
  
  def readInstanceResults(instanceId: Long, shouldBroadcast: Boolean): AggregatedReadInstanceResultsResponse
  
  def readPdo(patientSetCollId: String, optionsXml: NodeSeq, shouldBroadcast: Boolean): ReadPdoResponse
  
  def readQueryDefinition(queryId: Long, shouldBroadcast: Boolean): ReadQueryDefinitionResponse
  
  def deleteQuery(queryId: Long, shouldBroadcast: Boolean): DeleteQueryResponse
  
  def renameQuery(queryId: Long, queryName: String, shouldBroadcast: Boolean): RenameQueryResponse
  
  //Overloads for Java interop
  import scala.collection.JavaConverters._

  def runQuery(topicId: String, outputTypes: java.util.Set[ResultOutputType], queryDefinition: QueryDefinition, shouldBroadcast: Boolean): AggregatedRunQueryResponse = runQuery(topicId, outputTypes.asScala.toSet, queryDefinition, shouldBroadcast)
  
  def readQueryResult(queryId: Long, shouldBroadcast: Boolean): AggregatedReadQueryResultResponse
}