package net.shrine.adapter

import xml.NodeSeq
import org.spin.tools.crypto.signature.Identity
import net.shrine.protocol._
import net.shrine.config.HiveCredentials
import net.shrine.util.HttpClient
import net.shrine.serialization.XmlMarshaller
import net.shrine.adapter.dao.AdapterDao

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
class RenameQueryAdapter(dao: AdapterDao) extends Adapter {
  override protected[adapter] def processRequest(identity: Identity, message: BroadcastMessage): XmlMarshaller = {
    val request = message.request.asInstanceOf[RenameQueryRequest]
    
    dao.renameQuery(request.queryId, request.queryName)
    
    RenameQueryResponse(request.queryId, request.queryName)
  }
}