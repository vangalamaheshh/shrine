package net.shrine.protocol

import xml.NodeSeq
import net.shrine.protocol.CRCRequestType.InstanceRequestType
import net.shrine.util.XmlUtil
import net.shrine.serialization.I2b2Unmarshaller

/**
 * @author Bill Simons
 * @date 3/17/11
 * @link http://cbmi.med.harvard.edu
 * @link http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @link http://www.gnu.org/licenses/lgpl.html
 *
 * NB: this is a case class to get a structural equality contract in hashCode and equals, mostly for testing
 */
final case class ReadInstanceResultsRequest(
  override val projectId: String,
  override val waitTimeMs: Long,
  override val authn: AuthenticationInfo,
  val instanceId: Long) extends ShrineRequest(projectId, waitTimeMs, authn) with CrcRequest with TranslatableRequest[ReadInstanceResultsRequest] {

  val requestType = InstanceRequestType

  def toXml = XmlUtil.stripWhitespace(
    <readInstanceResults>
      { headerFragment }
      <instanceId>{ instanceId }</instanceId>
    </readInstanceResults>)

  def handle(handler: ShrineRequestHandler) = {
    handler.readInstanceResults(this)
  }

  def withId(id: Long) = new ReadInstanceResultsRequest(projectId, waitTimeMs, authn, id)

  def withProject(proj: String) = new ReadInstanceResultsRequest(proj, waitTimeMs, authn, instanceId)

  def withAuthn(ai: AuthenticationInfo) = new ReadInstanceResultsRequest(projectId, waitTimeMs, ai, instanceId)

  protected def i2b2MessageBody = XmlUtil.stripWhitespace(
    <message_body>
      { i2b2PsmHeader }
      <ns4:request xsi:type="ns4:instance_requestType" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <query_instance_id>{ instanceId }</query_instance_id>
      </ns4:request>
    </message_body>)
}

object ReadInstanceResultsRequest extends I2b2Unmarshaller[ReadInstanceResultsRequest] with ShrineRequestUnmarshaller[ReadInstanceResultsRequest] {

  def fromI2b2(nodeSeq: NodeSeq): ReadInstanceResultsRequest = {
    new ReadInstanceResultsRequest(
      i2b2ProjectId(nodeSeq),
      i2b2WaitTimeMs(nodeSeq),
      i2b2AuthenticationInfo(nodeSeq),
      (nodeSeq \ "message_body" \ "request" \ "query_instance_id").text.toLong)
  }

  def fromXml(nodeSeq: NodeSeq) = {
    new ReadInstanceResultsRequest(
      shrineProjectId(nodeSeq),
      shrineWaitTimeMs(nodeSeq),
      shrineAuthenticationInfo(nodeSeq),
      (nodeSeq \ "instanceId").text.toLong)

  }
}