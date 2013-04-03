package net.shrine.protocol

import CrcRequestType._
import scala.xml.NodeSeq

/**
 * @author clint
 * @date Mar 29, 2013
 */
abstract class DoubleDispatchingShrineRequest(
    override val projectId: String,
    override val waitTimeMs: Long,
    override val authn: AuthenticationInfo) extends ShrineRequest(projectId, waitTimeMs, authn) {
  
  type Handler

  def handle(handler: Handler, shouldBroadcast: Boolean): ShrineResponse
}

object DoubleDispatchingShrineRequest extends AbstractUnmarshallerCompanion[DoubleDispatchingShrineRequest](
  Map(
    InstanceRequestType -> ReadInstanceResultsRequest,
    UserRequestType -> ReadPreviousQueriesRequest,
    GetRequestXml -> ReadQueryDefinitionRequest,
    MasterRequestType -> ReadQueryInstancesRequest,
    QueryDefinitionRequestType -> RunQueryRequest,
    MasterRenameRequestType -> RenameQueryRequest,
    MasterDeleteRequestType -> DeleteQueryRequest)) {
  
  override def fromI2b2(i2b2Request: NodeSeq): DoubleDispatchingShrineRequest = {
    i2b2Request match {
      case x if isPsmRequest(x) => parsePsmRequest(x)
      case x if isPdoRequest(x) => parsePdoRequest(x)
      case x if isSheriffRequest(x) => parseSheriffRequest(x)
      case _ => throw new Exception(s"Request not understood: $i2b2Request")
    }
  }
  
  import CrcRequestType._

  private def parsePdoRequest(requestXml: NodeSeq): ReadPdoRequest = {
    (requestXml \ "message_body" \ "pdoheader" \ "request_type").text match {
      case x if x == GetPDOFromInputListRequestType.i2b2RequestType => ReadPdoRequest.fromI2b2(requestXml)
      case _ => null
    }
  }

  private def parseSheriffRequest(requestXml: NodeSeq): ReadApprovedQueryTopicsRequest = {
    ReadApprovedQueryTopicsRequest.fromI2b2(requestXml)
  }
}