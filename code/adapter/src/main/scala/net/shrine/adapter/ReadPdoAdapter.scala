package net.shrine.adapter

import xml.NodeSeq
import net.shrine.protocol._
import net.shrine.config.HiveCredentials
import net.shrine.util.HttpClient

/**
 * @author ??? (Dave Ortiz? Justin Quan?)
 * @date ???
 */
class ReadPdoAdapter(
    crcUrl: String,
    httpClient: HttpClient,
    override protected val hiveCredentials: HiveCredentials)
    extends CrcAdapter[ReadPdoRequest, ReadPdoResponse](crcUrl, httpClient, hiveCredentials) {

  override protected def parseShrineResponse(nodeSeq: NodeSeq) = ReadPdoResponse.fromI2b2(nodeSeq)
}
