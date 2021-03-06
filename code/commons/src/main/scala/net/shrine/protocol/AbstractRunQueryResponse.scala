package net.shrine.protocol

import scala.xml.Atom
import scala.xml.Elem
import scala.xml.NodeSeq

import org.spin.tools.NetworkTime.makeXMLGregorianCalendar

import javax.xml.datatype.XMLGregorianCalendar
import net.shrine.protocol.query.QueryDefinition
import net.shrine.util.XmlUtil
import net.shrine.serialization.{ I2b2Unmarshaller, XmlUnmarshaller }

/**
 * @author clint
 * @date Nov 29, 2012
 */
abstract class AbstractRunQueryResponse(
    rootTagName: String,
    val queryId: Long,
    val createDate: XMLGregorianCalendar,
    val userId: String,
    val groupId: String,
    val requestXml: QueryDefinition,
    val queryInstanceId: Long) extends ShrineResponse with HasQueryResults {

  type ActualResponseType <: AbstractRunQueryResponse

  def withId(id: Long): ActualResponseType

  def withInstanceId(id: Long): ActualResponseType

  final def queryName = requestXml.name

  override protected final def i2b2MessageBody = XmlUtil.stripWhitespace {
    <ns5:response xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:master_instance_result_responseType">
      <status>
        <condition type="DONE">DONE</condition>
      </status>
      <query_master>
        <query_master_id>{ queryId }</query_master_id>
        <name>{ queryName }</name>
        <user_id>{ userId }</user_id>
        <group_id>{ groupId }</group_id>
        <create_date>{ createDate }</create_date>
        <request_xml>{ requestXml.toI2b2 }</request_xml>
      </query_master>
      <query_instance>
        <query_instance_id>{ queryInstanceId }</query_instance_id>
        <query_master_id>{ queryId }</query_master_id>
        <user_id>{ userId }</user_id>
        <group_id>{ groupId }</group_id>
        <query_status_type>
          <status_type_id>6</status_type_id>
          <name>COMPLETED</name>
          <description>COMPLETED</description>
        </query_status_type>
      </query_instance>
      {
        results.map(_.withInstanceId(queryInstanceId).toI2b2)
      }
    </ns5:response>
  }

  override final def toXml = XmlUtil.stripWhitespace {
    XmlUtil.renameRootTag(rootTagName) {
      <placeHolder>
        <queryId>{ queryId }</queryId>
        <instanceId>{ queryInstanceId }</instanceId>
        <userId>{ userId }</userId>
        <groupId>{ groupId }</groupId>
        <requestXml>{ requestXml.toXml }</requestXml>
        <createDate>{ createDate }</createDate>
        <queryResults>
          {
            results.map(_.withInstanceId(queryInstanceId).toXml)
          }
        </queryResults>
      </placeHolder>
    }
  }
}

object AbstractRunQueryResponse {
  //
  //NB: Creatable trait and companion object implement the typeclass pattern:
  //http://www.casualmiracles.com/2012/05/03/a-small-example-of-the-typeclass-pattern-in-scala/
  //A typeclass is used here in place of an abstract method with multiple concrete implementations,
  //or another similar strategy. -Clint
  
  private trait Creatable[T] {
    def apply(queryId: Long, createDate: XMLGregorianCalendar, userId: String, groupId: String, requestXml: QueryDefinition, queryInstanceId: Long, results: Seq[QueryResult]): T
  }

  private object Creatable {
    implicit val runQueryResponseIsCreatable: Creatable[RunQueryResponse] = new Creatable[RunQueryResponse] {
      override def apply(queryId: Long, createDate: XMLGregorianCalendar, userId: String, groupId: String, requestXml: QueryDefinition, queryInstanceId: Long, results: Seq[QueryResult]) = {
        RunQueryResponse(queryId, createDate, userId, groupId, requestXml, queryInstanceId, results.head)
      }
    }

    implicit val aggregatedRunQueryResponseIsCreatable: Creatable[AggregatedRunQueryResponse] = new Creatable[AggregatedRunQueryResponse] {
      override def apply(queryId: Long, createDate: XMLGregorianCalendar, userId: String, groupId: String, requestXml: QueryDefinition, queryInstanceId: Long, results: Seq[QueryResult]) = {
        AggregatedRunQueryResponse(queryId, createDate, userId, groupId, requestXml, queryInstanceId, results)
      }
    }

    implicit val rawCrcRunQueryResponseIsCreatable: Creatable[RawCrcRunQueryResponse] = new Creatable[RawCrcRunQueryResponse] {
      override def apply(queryId: Long, createDate: XMLGregorianCalendar, userId: String, groupId: String, requestXml: QueryDefinition, queryInstanceId: Long, results: Seq[QueryResult]) = {
        val queryResultMap = RawCrcRunQueryResponse.toQueryResultMap(results)

        RawCrcRunQueryResponse(queryId, createDate, userId, groupId, requestXml, queryInstanceId, queryResultMap)
      }
    }
  }

  abstract class Companion[R <: AbstractRunQueryResponse: Creatable] extends I2b2Unmarshaller[R] with XmlUnmarshaller[R] {
    private val createResponse = implicitly[Creatable[R]]

    import org.spin.tools.NetworkTime.makeXMLGregorianCalendar

    override def fromI2b2(nodeSeq: NodeSeq): R = {
      def firstChild(nodeSeq: NodeSeq) = nodeSeq.head.asInstanceOf[Elem].child.head

      val responseXml = nodeSeq \ "message_body" \ "response"

      val queryMasterXml = responseXml \ "query_master"

      val results = (responseXml \ "query_result_instance").map(QueryResult.fromI2b2)
      val queryId = (queryMasterXml \ "query_master_id").text.toLong
      val userId = (queryMasterXml \ "user_id").text
      val groupId = (queryMasterXml \ "group_id").text
      val createDate = (queryMasterXml \ "create_date").text

      def asString(a: Atom[_]): String = a.data.asInstanceOf[String]

      def holdsString(a: Atom[_]): Boolean = a.data.isInstanceOf[String]

      //NB: We need to handle the query def situtation two different ways:
      // 1) Where the query def is an escaped String, as is returned by the CRC
      // 2) Where the query def is plain XML, as is provided by Shrine
      val requestXml = firstChild(queryMasterXml \ "request_xml") match {
        case a: Atom[_] if holdsString(a) => QueryDefinition.fromI2b2(asString(a))
        case xml: NodeSeq => QueryDefinition.fromI2b2(xml)
      }

      val queryInstanceId = (responseXml \ "query_instance" \ "query_instance_id").text.toLong

      //TODO: Remove unsafe requestXml.get call
      createResponse(queryId, makeXMLGregorianCalendar(createDate), userId, groupId, requestXml.get, queryInstanceId, results)
    }

    override def fromXml(nodeSeq: NodeSeq): R = {
      val results = (nodeSeq \ "queryResults" \ "_").map(QueryResult.fromXml)

      createResponse(
        (nodeSeq \ "queryId").text.toLong,
        makeXMLGregorianCalendar((nodeSeq \ "createDate").text),
        (nodeSeq \ "userId").text,
        (nodeSeq \ "groupId").text,
        QueryDefinition.fromXml(nodeSeq \ "requestXml" \ "queryDefinition").get, //TODO: Remove unsafe get call
        (nodeSeq \ "instanceId").text.toLong,
        results)
    }
  }
}