package net.shrine.adapter

import dao.AdapterDAO
import xml.{NodeSeq, XML}
import org.spin.tools.crypto.signature.Identity
import net.shrine.protocol._
import net.shrine.config.HiveCredentials
import net.shrine.util.HttpClient

/**
 * @author Bill Simons
 * @date 4/11/11
 * @link http://cbmi.med.harvard.edu
 * @link http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @link http://www.gnu.org/licenses/lgpl.html
 */
abstract class CrcAdapter[T <: ShrineRequest, V <: ShrineResponse](
    crcUrl: String,
    httpClient: HttpClient,
    override protected val dao: AdapterDAO,
    override protected val hiveCredentials: HiveCredentials) extends Adapter(dao, hiveCredentials) {

  protected def translateNetworkToLocal(request: T): T

  protected def translateLocalToNetwork(response: V): V

  protected def parseShrineResponse(nodeSeq: NodeSeq): ShrineResponse

  protected def processRequest(identity: Identity, message: BroadcastMessage): ShrineResponse = {
    val i2b2Response = callCrc(translateRequest(message.request))
    
    val shrineResponse = parseShrineResponse(XML.loadString(i2b2Response))
    
    translateResponse(shrineResponse)
  }

  private def translateRequest(request: ShrineRequest): ShrineRequest = {
    val HiveCredentials(domain, username, password, project) = hiveCredentials
    
    def authInfo = AuthenticationInfo(domain, username, Credential(password, false))

    request match {
      case transReq: TranslatableRequest[T] => {
        translateNetworkToLocal(transReq.withAuthn(authInfo).withProject(project).asRequest)
      }
      case _ => request
    }
  }

  private def translateResponse(response: ShrineResponse): ShrineResponse = {
    response match {
      case transResp: TranslatableResponse[V] => {
        translateLocalToNetwork(transResp.asResponse)
      }
      case _ => response
    }
  }

  protected def callCrc(request: ShrineRequest): String = {
    val crcRequest = request.toI2b2String
    
    debug(String.format("Request to CRC:\r\n%s", crcRequest))
    
    val crcResponse = httpClient.post(crcRequest, crcUrl)
    
    debug(String.format("Response from CRC:\r\n%s", crcResponse))
    
    crcResponse
  }
}