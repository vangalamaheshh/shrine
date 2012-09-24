package net.shrine.adapter

import dao.AdapterDAO
import xml.NodeSeq
import net.shrine.protocol._
import net.shrine.config.HiveCredentials
import net.shrine.util.HttpClient

class ReadPdoAdapter(
    crcUrl: String,
    httpClient: HttpClient,
    dao: AdapterDAO,
    hiveCredentials: HiveCredentials)
    extends CrcAdapter[ReadPdoRequest, ReadPdoResponse](crcUrl, httpClient, dao, hiveCredentials) {

  protected def parseShrineResponse(nodeSeq: NodeSeq) = ReadPdoResponse.fromI2b2(nodeSeq)

  protected def translateLocalToNetwork(response: ReadPdoResponse): ReadPdoResponse = response

  protected[adapter] def translateNetworkToLocal(request: ReadPdoRequest) = {
    val localPatientCollId = dao.findLocalResultID(java.lang.Long.parseLong(request.patientSetCollId))
    request.withPatientSetCollId(localPatientCollId)
  }
}
