package net.shrine.protocol

import xml.{NodeSeq, Utility, NodeBuffer}
import org.scalatest.junit.{AssertionsForJUnit, ShouldMatchersForJUnit}
import net.shrine.util.XmlUtil

/**
 * @author Bill Simons
 * @date 3/16/11
 * @link http://cbmi.med.harvard.edu
 * @link http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @link http://www.gnu.org/licenses/lgpl.html
 */
trait ShrineRequestValidator extends AssertionsForJUnit with ShouldMatchersForJUnit with XmlSerializableValidator with I2b2SerializableValidator {
  val projectId = "test_project_id"
  val domain = "test_domain"
  val username = "test_username"
  val passwd = "passwd"
  val waitTimeMs = 180000

  def messageBody: NodeSeq

  def testShrineRequestFromI2b2

  def request = XmlUtil.stripWhitespace(
    <ns6:request xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns8="http://sheriff.shrine.net/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/plugin/" xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/hive/msg/1.1/">
      <message_header>
        <proxy>
          <redirect_url>https://localhost/shrine/QueryToolService/request</redirect_url>
        </proxy>
        <sending_application>
          <application_name>i2b2_QueryTool</application_name>
          <application_version>0.2</application_version>
        </sending_application>
        <sending_facility>
          <facility_name>SHRINE</facility_name>
        </sending_facility>
        <receiving_application>
          <application_name>i2b2_DataRepositoryCell</application_name>
          <application_version>0.2</application_version>
        </receiving_application>
        <receiving_facility>
          <facility_name>SHRINE</facility_name>
        </receiving_facility>
        <security>
          <domain>{domain}</domain>
          <username>{username}</username>
          <password token_ms_timeout="1800000" is_token="false">{passwd}</password>
        </security>
        <message_type>
          <message_code>Q04</message_code>
          <event_type>EQQ</event_type>
        </message_type>
        <message_control_id>
          <message_num>EQ7Szep1Md11K4E7zEc99</message_num>
          <instance_num>0</instance_num>
        </message_control_id>
        <processing_id>
          <processing_id>P</processing_id>
          <processing_mode>I</processing_mode>
        </processing_id>
        <accept_acknowledgement_type>AL</accept_acknowledgement_type>
        <project_id>{projectId}</project_id>
        <country_code>US</country_code>
      </message_header>
      <request_header>
        <result_waittime_ms>{waitTimeMs}</result_waittime_ms>
      </request_header>
      {messageBody}
    </ns6:request>)

  val credential = new Credential(passwd, false)
  
  val authn = new AuthenticationInfo(domain, username, credential)

  def requestHeaderFragment: NodeBuffer = <projectId>{projectId}</projectId><waitTimeMs>{waitTimeMs}</waitTimeMs> &+ authn.toXml

  private def validateBaseRequest(translatedRequest: ShrineRequest) = {
    translatedRequest.projectId should equal(projectId)
    translatedRequest.waitTimeMs should equal(waitTimeMs)
    translatedRequest.authn.domain should equal(domain)
    translatedRequest.authn.username should equal(username)
    translatedRequest.authn.credential should equal(credential)
  }

  def validateRequestWith(request: ShrineRequest)(validator: => Unit) = {
    validateBaseRequest(request)
    
    validator
  }
}