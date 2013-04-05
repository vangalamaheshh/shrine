package net.shrine.adapter.components

import junit.framework.TestCase
import org.scalatest.junit.ShouldMatchersForJUnit
import org.junit.Test
import net.shrine.util.HttpClient
import net.shrine.util.Loggable
import net.shrine.i2b2.protocol.pm.User
import net.shrine.protocol.Credential
import net.shrine.util.XmlUtil
import net.shrine.protocol.AuthenticationInfo
import net.shrine.i2b2.protocol.pm.GetUserConfigurationRequest

/**
 * @author clint
 * @date Apr 5, 2013
 */
final class PmAuthorizerComponentTest extends TestCase with ShouldMatchersForJUnit {
  import PmAuthorizerComponentTest._
  
  import PmAuthorizerComponent._
  
  @Test
  def testGoodResponse {
    val httpClient = new MockHttpClient(validUserResponseXml.toString)
    
    val component = new TestPmAuthorizerComponent(httpClient)
    
    val Authorized(user) = component.Pm.authorize(authn)
    
    user should not be(null)
    user.fullName should be(fullName)
    user.username should be(username)
    user.domain should be(domain)
    user.credential should be(authn.credential)
    
    /*val expectedAuthRequestXml = GetUserConfigurationRequest(authn.domain, authn.username, authn.credential.value).toI2b2String
    
    httpClient.inputParam.get should equal(expectedAuthRequestXml)*/
  }
  
  @Test
  def testErrorResponse {
    val component = new TestPmAuthorizerComponent(new MockHttpClient(i2b2ErrorXml.toString))
    
    val NotAuthorized(reason) = component.Pm.authorize(authn)
    
    reason should be(errorMessage)
  } 
  
  @Test
  def testJunkResponse {
    val component = new TestPmAuthorizerComponent(new MockHttpClient("jahfskajhkjashfdkjashkfd"))
    
    val NotAuthorized(reason) = component.Pm.authorize(authn)
    
    reason should not be(null)
    reason.size should not be(0)
  } 
  
  @Test
  def testResponseThatBlowsUp {
    val component = new TestPmAuthorizerComponent(new MockHttpClient(throw new Exception with scala.util.control.NoStackTrace))
    
    val NotAuthorized(reason) = component.Pm.authorize(authn)
    
    reason should not be(null)
    reason.size should not be(0)
  }
  
  private val fullName = "Some Person"
  private val username = "some-user"
  private val domain = "some-place"
  private val password = "sal;dk;aslkd;"
  private val errorMessage = "Something blew up"

  private lazy val authn = AuthenticationInfo(domain, username, Credential(password, true))
    
  private lazy val validUserResponseXml = XmlUtil.stripWhitespace {
    <ns4:response xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns7="http://sheriff.shrine.net/" xmlns:ns8="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns11="http://www.i2b2.org/xsd/hive/msg/result/1.1/">
      <message_header>
        <i2b2_version_compatible>1.1</i2b2_version_compatible>
        <hl7_version_compatible>2.4</hl7_version_compatible>
        <sending_application>
          <application_name>SHRINE</application_name>
          <application_version>1.3-compatible</application_version>
        </sending_application>
        <sending_facility>
          <facility_name>SHRINE</facility_name>
        </sending_facility>
        <datetime_of_message>2011-04-08T16:21:12.251-04:00</datetime_of_message>
        <security/>
        <project_id xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/>
      </message_header>
      <response_header>
        <result_status>
          <status type="DONE">DONE</status>
        </result_status>
      </response_header>
      <message_body>
        <ns6:configure>
          <environment>DEVELOPMENT</environment>
          <helpURL>http://www.i2b2.org</helpURL>
          <user>
            <full_name>{fullName}</full_name>
            <user_name>{username}</user_name>
            <password token_ms_timeout="1800000" is_token="true">{password}</password>
            <domain>{domain}</domain>
            <param name="ecommons_username">ecommons</param>
            <param name="other_param">other</param>
            <project id="Demo">
              <name>Demo Group</name>
              <wiki>http://www.i2b2.org</wiki>
              <role>DATA_OBFSC</role>
            </project>
          </user>
          <cell_datas>
            <cell_data id="CRC">
              <name>Data Repository</name>
              <url>http://localhost/crc</url>
              <method>REST</method>
            </cell_data>
            <cell_data id="ONT">
              <name>Ontology Cell</name>
              <url>http://localhost/ont</url>
              <method>REST</method>
              <param name="OntSynonyms">false</param>
              <param name="OntMax">200</param>
              <param name="OntHidden">false</param>
            </cell_data>
          </cell_datas>
        </ns6:configure>
      </message_body>
    </ns4:response>
  }
  
  private val i2b2ErrorXml = XmlUtil.stripWhitespace {
    <ns4:response xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns7="http://sheriff.shrine.net/" xmlns:ns8="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns11="http://www.i2b2.org/xsd/hive/msg/result/1.1/">
      <message_header>
        <i2b2_version_compatible>1.1</i2b2_version_compatible>
        <hl7_version_compatible>2.4</hl7_version_compatible>
        <sending_application>
          <application_name>SHRINE</application_name>
          <application_version>1.3-compatible</application_version>
        </sending_application>
        <sending_facility>
          <facility_name>SHRINE</facility_name>
        </sending_facility>
        <datetime_of_message>2011-04-08T16:21:12.251-04:00</datetime_of_message>
        <security/>
        <project_id xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/>
      </message_header>
      <response_header>
        <result_status>
          <status type="ERROR">{ errorMessage }</status>
        </result_status>
      </response_header>
      <message_body>
      </message_body>
    </ns4:response>
  }
}

object PmAuthorizerComponentTest {
  final class TestPmAuthorizerComponent(override val httpClient: HttpClient) extends PmAuthorizerComponent with PmHttpClientComponent with Loggable {
    override val pmEndpoint = "foo"
  }
}