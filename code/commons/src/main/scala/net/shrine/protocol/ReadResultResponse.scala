package net.shrine.protocol

import scala.xml.NodeSeq

import net.shrine.serialization.I2b2Unmarshaller
import net.shrine.serialization.XmlUnmarshaller
import net.shrine.util.XmlUtil

/**
 * @author clint
 * @date Aug 17, 2012
 */
final case class ReadResultResponse(xmlResultId: Long, metadata: QueryResult, data: I2b2ResultEnvelope) extends ShrineResponse {
  override protected def i2b2MessageBody: NodeSeq = XmlUtil.stripWhitespace(
    <ns4:response xsi:type="ns4:crc_xml_result_responseType">
      <status>
        <condition type="DONE">DONE</condition>
      </status>
      { metadata.toI2b2 }
      <crc_xml_result>
        <xml_result_id>{ xmlResultId }</xml_result_id>
        <result_instance_id>{ metadata.resultId }</result_instance_id>
        <xml_value>
          { 
            I2b2Workarounds.escape("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""") + 
            I2b2Workarounds.escape(data.toI2b2String)
          }
        </xml_value>
      </crc_xml_result>
    </ns4:response>)

  //xmlResultId doesn't seem necessary, but I wanted to allow Shrine => I2b2 => Shrine marshalling loops without losing anything.  
  //Maybe this isn't needed? 
  override def toXml: NodeSeq = XmlUtil.stripWhitespace(
    <readResultResponse>
      { metadata.toXml }
      <xmlResultId>{ xmlResultId }</xmlResultId>
      { data.toXml }
    </readResultResponse>)
}

object ReadResultResponse extends XmlUnmarshaller[ReadResultResponse] with I2b2Unmarshaller[ReadResultResponse] {
  override def fromXml(xml: NodeSeq): ReadResultResponse = unmarshal(xml,
    x => I2b2ResultEnvelope.fromXml(x \ "resultEnvelope"),
    x => QueryResult.fromXml(x \ "queryResult"),
    x => (x \ "xmlResultId").text.toLong)

  private[this] def messageBodyXml(x: NodeSeq) = x \ "message_body"

  private[this] def responseXml(x: NodeSeq) = messageBodyXml(x) \ "response"

  private[this] def crcResultXml(x: NodeSeq) = responseXml(x) \ "crc_xml_result"

  override def fromI2b2(xml: NodeSeq): ReadResultResponse = {
    def envelope(x: NodeSeq) = {
      val unescapedXml = I2b2Workarounds.unescape((crcResultXml(x) \ "xml_value").text)

      try {
        I2b2ResultEnvelope.fromI2b2(unescapedXml)
      } catch {
        case e: Exception => throw new Exception("Error unmarshalling an " + I2b2ResultEnvelope.getClass.getSimpleName + " from '" + x.toString + "'")
      }
    }

    def getXmlResultId(x: NodeSeq) = (crcResultXml(x) \ "xml_result_id").text.toLong

    unmarshal(xml,
      envelope,
      x => QueryResult.fromI2b2(responseXml(x) \ "query_result_instance"),
      getXmlResultId)
  }

  private def unmarshal(xml: NodeSeq,
    getData: NodeSeq => Option[I2b2ResultEnvelope],
    metadata: NodeSeq => QueryResult,
    xmlResultId: NodeSeq => Long): ReadResultResponse = {
    (for {
      data <- getData(xml)
    } yield ReadResultResponse(xmlResultId(xml), metadata(xml), data)).get
  }
}
