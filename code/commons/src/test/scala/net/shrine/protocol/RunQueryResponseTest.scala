package net.shrine.protocol

import org.junit.Test
import org.spin.tools.NetworkTime
import java.util.Date
import xml.{ XML, Utility }
import net.shrine.util.XmlUtil
import net.shrine.protocol.query.QueryDefinition
import net.shrine.protocol.query.OccuranceLimited
import net.shrine.protocol.query.Term
import scala.xml.NodeSeq
import scala.xml.Text

/**
 *
 *
 * @author Justin Quan
 * @link http://chip.org
 * Date: 8/12/11
 */
final class RunQueryResponseTest extends ShrineResponseI2b2SerializableValidator {
  private val queryId = 1L
  private val queryName = "queryName"
  private val userId = "user"
  private val groupId = "group"
  private val createDate = NetworkTime.makeXMLGregorianCalendar(new Date())
  private val requestQueryDef = QueryDefinition(queryName, Term("""\\i2b2\i2b2\Demographics\Age\0-9 years old\"""))
  private val queryInstanceId = 2L
  private val resultId = 3L
  private val description = Option("description")
  private val setSize = 10L
  private val startDate = createDate
  private val endDate = createDate
  private val resultId2 = 4L
  private val resultType1 = ResultOutputType.PATIENTSET
  private val resultType2 = ResultOutputType.PATIENT_COUNT_XML
  private val statusType = QueryResult.StatusType.Finished

  override def messageBody = <message_body>
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
                          <request_xml>{ requestQueryDef.toI2b2 }</request_xml>
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
                        <query_result_instance>
                          <result_instance_id>{ resultId }</result_instance_id>
                          <query_instance_id>{ queryInstanceId }</query_instance_id>
                          <query_result_type>
                            <name>{ resultType1 }</name>
                            <result_type_id>1</result_type_id>
                            <display_type>LIST</display_type>
                            <visual_attribute_type>LA</visual_attribute_type>
                            <description>Patient list</description>
                          </query_result_type>
                          <set_size>{ setSize }</set_size>
                          <start_date>{ startDate }</start_date>
                          <end_date>{ endDate }</end_date>
                          <query_status_type>
                            <name>{ statusType }</name>
                            <status_type_id>3</status_type_id>
                            <description>FINISHED</description>
                          </query_status_type>
                        </query_result_instance>
                      </ns5:response>
                    </message_body>

  private val qr1 = new QueryResult(resultId, queryInstanceId, resultType1, setSize, createDate, createDate, statusType)
  private val qr2 = new QueryResult(resultId2, queryInstanceId, resultType2, setSize, createDate, createDate, statusType)

  private val runQueryResponse = XmlUtil.stripWhitespace(
    <runQueryResponse>
      <queryId>{ queryId }</queryId>
      <instanceId>{ queryInstanceId }</instanceId>
      <userId>{ userId }</userId>
      <groupId>{ groupId }</groupId>
      <requestXml>{ requestQueryDef.toXml }</requestXml>
      <createDate>{ createDate }</createDate>
      <queryResults>
        { qr1.toXml }
      </queryResults>
    </runQueryResponse>)

  @Test
  def testFromXml {
    val actual = RunQueryResponse.fromXml(runQueryResponse)

    actual.queryId should equal(queryId)
    actual.createDate should equal(createDate)
    actual.userId should equal(userId)
    actual.groupId should equal(groupId)
    actual.requestXml should equal(requestQueryDef)
    actual.queryInstanceId should equal(queryInstanceId)
    actual.results should equal(Seq(qr1))
    actual.queryName should equal(queryName)
  }

  @Test
  def testToXml {
    RunQueryResponse(queryId, createDate, userId, groupId, requestQueryDef, queryInstanceId, qr1).toXmlString should equal(runQueryResponse.toString)
  }

  @Test
  def testFromI2b2 {
    val translatedResponse = RunQueryResponse.fromI2b2(response)
    
    translatedResponse.queryId should equal(queryId)
    translatedResponse.createDate should equal(createDate)
    translatedResponse.userId should equal(userId)
    translatedResponse.groupId should equal(groupId)
    translatedResponse.requestXml should equal(requestQueryDef)
    translatedResponse.queryInstanceId should equal(queryInstanceId)
    translatedResponse.results should equal(Seq(qr1))
    translatedResponse.queryName should equal(queryName)
  }

  @Test
  def testFromI2b2StringRequestXml {
    val hackToProduceXml = new HasResponse {
      //Produces a message body where the <request_xml> tag contains escaped XML as a String, as is produced by the CRC
      override def messageBody: NodeSeq = <message_body>
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
                                                <!-- Because requestQueryDef is turned to a String, it will be escaped in the XML, -->
                                                <!-- the handling of which is what we want to test. -->
                                                <request_xml>{ requestQueryDef.toI2b2String }</request_xml>
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
                                              <query_result_instance>
                                                <result_instance_id>{ resultId }</result_instance_id>
                                                <query_instance_id>{ queryInstanceId }</query_instance_id>
                                                <query_result_type>
                                                  <name>{ resultType1 }</name>
                                                  <result_type_id>1</result_type_id>
                                                  <display_type>LIST</display_type>
                                                  <visual_attribute_type>LA</visual_attribute_type>
                                                  <description>Patient list</description>
                                                </query_result_type>
                                                <set_size>{ setSize }</set_size>
                                                <start_date>{ startDate }</start_date>
                                                <end_date>{ endDate }</end_date>
                                                <query_status_type>
                                                  <name>{ statusType }</name>
                                                  <status_type_id>3</status_type_id>
                                                  <description>FINISHED</description>
                                                </query_status_type>
                                              </query_result_instance>
                                              <query_result_instance>
                                                <result_instance_id>{ resultId2 }</result_instance_id>
                                                <query_instance_id>{ queryInstanceId }</query_instance_id>
                                                <query_result_type>
                                                  <name>{ resultType2 }</name>
                                                  <result_type_id>4</result_type_id>
                                                  <display_type>CATNUM</display_type>
                                                  <visual_attribute_type>LA</visual_attribute_type>
                                                  <description>Number of patients</description>
                                                </query_result_type>
                                                <set_size>{ setSize }</set_size>
                                                <start_date>{ startDate }</start_date>
                                                <end_date>{ endDate }</end_date>
                                                <query_status_type>
                                                  <name>{ statusType }</name>
                                                  <status_type_id>3</status_type_id>
                                                  <description>FINISHED</description>
                                                </query_status_type>
                                              </query_result_instance>
                                            </ns5:response>
                                          </message_body>
    }

    doTestFromI2b2(hackToProduceXml.response, requestQueryDef)
  }

  private def doTestFromI2b2(i2b2Response: NodeSeq, expectedQueryDef: AnyRef) {
    val translatedResponse = RunQueryResponse.fromI2b2(i2b2Response)

    translatedResponse.queryId should equal(queryId)
    translatedResponse.createDate should equal(createDate)
    translatedResponse.userId should equal(userId)
    translatedResponse.groupId should equal(groupId)
    translatedResponse.requestXml should equal(expectedQueryDef)
    translatedResponse.queryInstanceId should equal(queryInstanceId)
    translatedResponse.results should equal(Seq(qr1))
    translatedResponse.queryName should equal(queryName)
  }

  @Test
  def testToI2b2 {
    RunQueryResponse(queryId, createDate, userId, groupId, requestQueryDef, queryInstanceId, qr1).toI2b2String should equal(response.toString)
  }
}