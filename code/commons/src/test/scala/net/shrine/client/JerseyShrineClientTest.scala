package net.shrine.client

import junit.framework.TestCase
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.junit.ShouldMatchersForJUnit
import scala.xml.NodeSeq
import net.shrine.protocol._
import net.shrine.protocol.query.QueryDefinition
import net.shrine.protocol.query.Term
import org.spin.tools.NetworkTime
import org.spin.tools.RandomTool.randomString


/**
 *
 * @author Clint Gilbert
 * @date Sep 19, 2011
 *
 * @link http://cbmi.med.harvard.edu
 *
 * This software is licensed under the LGPL
 * @link http://www.gnu.org/licenses/lgpl.html
 *
 * A client for remote ShrineResources, implemented using Jersey
 *
 */
final class JerseyShrineClientTest extends TestCase with AssertionsForJUnit with ShouldMatchersForJUnit {
  
  private val uri = "http://example.com"
  private val projectId = "alkjdasld"
  private val authn = new AuthenticationInfo("domain", "user", new Credential("skdhaskdhkaf", true))
  
  def testConstructor {
    val uri = "http://example.com"
    val projectId = "alkjdasld"
    val authn = new AuthenticationInfo("domain", "user", new Credential("skdhaskdhkaf", true))
    
    def doTestConstructor(client: JerseyShrineClient, expectedAcceptAllCertsValue: Boolean) {
      client should not be(null)
      client.shrineUrl should equal(uri)
      client.authorization should equal(authn)
      client.projectId should equal(projectId)
      client.acceptAllSslCerts should equal(expectedAcceptAllCertsValue)
    }
      
    doTestConstructor(new JerseyShrineClient(uri, projectId, authn, false), false)
    doTestConstructor(new JerseyShrineClient(uri, projectId, authn, true), true)
    //default value for acceptAllSslCerts should be false
    doTestConstructor(new JerseyShrineClient(uri, projectId, authn), false)
    
    intercept[IllegalArgumentException] {
      new JerseyShrineClient(null, projectId, authn)
    }
    
    intercept[IllegalArgumentException] {
      new JerseyShrineClient("aslkdfjaklsf", projectId, authn)
    }
    
    intercept[IllegalArgumentException] {
      new JerseyShrineClient(uri, null, authn)
    }
    
    intercept[IllegalArgumentException] {
      new JerseyShrineClient("aslkdfjaklsf", projectId, null)
    }
  }
  
  def testIsValidUrl {
    import JerseyShrineClient.isValidUrl

    isValidUrl(null) should be(false)
    isValidUrl("") should be(false)
    isValidUrl("aksfhkasfh") should be(false)
    isValidUrl("example.com") should be(false)

    isValidUrl("http://example.com") should be(true)
  }

  def testPerform {
    final case class Foo(x: String) {
      def toXml = <Foo><x>{ x }</x></Foo>
    }

    import JerseyShrineClient._
    
    implicit val fooDeserializer: Deserializer[Foo] = new Deserializer[Foo] {
      def apply(xml: NodeSeq) = new Foo((xml \ "x").text)
    }

    val value = "laskjdasjklfhkasf"

    val client = new JerseyShrineClient(uri, projectId, authn, acceptAllSslCerts = false)
    
    val unmarshalled: Foo = client.perform(true)(client.webResource, _ => Foo(value).toXml.toString)

    unmarshalled should not be (null)

    val Foo(unmarshalledValue) = unmarshalled

    unmarshalledValue should equal(value)
  }

  def testDeserializers {
    def doTestDeserializer[T <: ShrineResponse](response: T, deserialize: NodeSeq => T) {
      val roundTripped = deserialize(response.toXml)

      roundTripped should equal(response)
    }
    
    val queryResult1 = QueryResult(1L, 456L, Some(ResultOutputType.PATIENT_COUNT_XML), 123L, None, None, None, QueryResult.StatusType.Finished, None, Map.empty)
    val queryResult2 = QueryResult(2L, 456L, Some(ResultOutputType.PATIENT_COUNT_XML), 123L, None, None, None, QueryResult.StatusType.Finished, None, Map.empty)
    
    doTestDeserializer(new AggregatedRunQueryResponse(123L, now, "userId", "groupId", QueryDefinition("foo", Term("bar")), 456L, Seq(queryResult1, queryResult2)), JerseyShrineClient.Deserializer.aggregatedRunQueryResponseDeserializer)

    doTestDeserializer(new ReadApprovedQueryTopicsResponse(Seq(new ApprovedTopic(123L, "asjkhjkas"))), JerseyShrineClient.Deserializer.readApprovedQueryTopicsResponseDeserializer)

    doTestDeserializer(new ReadPreviousQueriesResponse(Some("userId"), Some("groupId"), Seq.empty), JerseyShrineClient.Deserializer.readPreviousQueriesResponseDeserializer)

    doTestDeserializer(new ReadQueryInstancesResponse(999L, "userId", "groupId", Seq.empty), JerseyShrineClient.Deserializer.readQueryInstancesResponseDeserializer)

    doTestDeserializer(new AggregatedReadInstanceResultsResponse(1337L, Seq(dummyQueryResult(1337L))), JerseyShrineClient.Deserializer.aggregatedReadInstanceResultsResponseDeserializer)
    
    doTestDeserializer(new AggregatedReadQueryResultResponse(1337L, Seq(dummyQueryResult(1337L))), JerseyShrineClient.Deserializer.aggregatedReadQueryResultResponseDeserializer)

    doTestDeserializer(new ReadPdoResponse(Seq(new EventResponse("event", "patient", None, None, Seq.empty)), Seq(new PatientResponse("patientId", Seq(paramResponse))), Seq(new ObservationResponse(None, "eventId", None, "patientId", None, None, None, "observerCode", "startDate", None, "valueTypeCode",None,None,None,None,None,None,None, Seq(paramResponse)))), JerseyShrineClient.Deserializer.readPdoResponseDeserializer)

    doTestDeserializer(new ReadQueryDefinitionResponse(87456L, "name", "userId", now, "<foo/>"), JerseyShrineClient.Deserializer.readQueryDefinitionResponseDeserializer)

    doTestDeserializer(new DeleteQueryResponse(56834756L), JerseyShrineClient.Deserializer.deleteQueryResponseDeserializer)

    doTestDeserializer(new RenameQueryResponse(56834756L, "some-name"), JerseyShrineClient.Deserializer.renameQueryResponseDeserializer)
  }

  private def now = (new NetworkTime).getXMLGregorianCalendar

  import ResultOutputType._
  
  private def dummyQueryResult(enclosingInstanceId: Long) = new QueryResult(123L, enclosingInstanceId, Some(PATIENT_COUNT_XML), 789L, None, None, Some("description"), QueryResult.StatusType.Finished, Some("statusMessage"), Map.empty)

  private def paramResponse = new ParamResponse(randomString, randomString, randomString)
}