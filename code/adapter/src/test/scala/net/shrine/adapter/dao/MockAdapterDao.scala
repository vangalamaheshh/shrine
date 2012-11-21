package net.shrine.adapter.dao

import net.shrine.adapter.dao.model.ShrineQuery
import net.shrine.adapter.dao.model.ShrineQueryResult
import net.shrine.protocol.AuthenticationInfo
import net.shrine.protocol.ErrorResponse
import net.shrine.protocol.QueryResult
import net.shrine.protocol.RunQueryResponse
import net.shrine.protocol.query.QueryDefinition
import net.shrine.protocol.ResultOutputType
import net.shrine.protocol.I2b2ResultEnvelope
import net.shrine.protocol.query.Expression
import org.spin.tools.crypto.signature.Identity

/**
 * @author clint
 * @date Oct 19, 2012
 */
object MockAdapterDao extends AdapterDao {
  override def insertQuery(networkId: Long, name: String, authn: AuthenticationInfo, queryExpr: Expression): Int = 0

  override def insertQueryResults(parentQueryId: Int, response: RunQueryResponse): Map[ResultOutputType, Seq[Int]] = Map.empty
  
  override def insertCountResult(resultId: Int, originalCount: Long, obfuscatedCount: Long): Unit = ()
  
  override def insertBreakdownResults(parentResultIds: Map[ResultOutputType, Seq[Int]], originalBreakdowns: Map[ResultOutputType, I2b2ResultEnvelope], obfuscatedBreakdowns: Map[ResultOutputType, I2b2ResultEnvelope]): Unit = ()
  
  override def insertErrorResult(parentResultId: Int, errormessage: String): Unit = ()
  
  override def findQueryByNetworkId(networkQueryId: Long): Option[ShrineQuery] = None
  
  override def findQueriesByUserAndDomain(domain: String, username: String): Seq[ShrineQuery] = Nil
  
  override def findResultsFor(networkQueryId: Long): Option[ShrineQueryResult] = None
  
  override def isUserLockedOut(id: Identity, defaultThreshold: Int): Boolean = false
}