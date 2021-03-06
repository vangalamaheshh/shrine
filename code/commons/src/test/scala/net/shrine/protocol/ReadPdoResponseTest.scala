package net.shrine.protocol

import org.junit.Test
import org.spin.tools.NetworkTime._
import scala.Some
import net.shrine.util.XmlUtil


/**
 * @author Dave Ortiz
 * @link http://cbmi.med.harvard.edu
 * @link http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @link http://www.gnu.org/licenses/lgpl.html
 */

class ReadPdoResponseTest extends ShrineResponseI2b2SerializableValidator {

  val patient1Param1 = new ParamResponse("vital_status_cd", "vital_status_cd", "N")
  val patient1Param2 = new ParamResponse("birth_date", "birth_date", "1985-11-17T00:00:00.000-05:00")
  val patient2Param1 = new ParamResponse("vital_status_cd", "vital_status_cd", "N")
  val patient2Param2 = new ParamResponse("birth_date", "birth_date", "1966-08-29T00:00:00.000-04:00")
  val event1 = new EventResponse(789012.toString, 1000000001.toString,
    Some(makeXMLGregorianCalendar("2011-01-29T00:00:00.000-05:00")),
    Some(makeXMLGregorianCalendar("2011-01-29T00:00:00.000-05:00")),
    List(patient1Param1, patient1Param2))
  val event2 = new EventResponse(123456.toString, 1000000001.toString, None, None,
    List(patient1Param1, patient1Param2))
  val patient1 = new PatientResponse(1000000001.toString, List(patient1Param1, patient1Param2))
  val patient2 = new PatientResponse(1000000002.toString, List(patient2Param1, patient2Param2))
  val observationEvent1 = <event_id>2005000001</event_id>
  val observationEvent2 = <event_id>2005000002</event_id>
  val observation1 = new ObservationResponse(Some("eventIdSource"), "eventId", Some("patientIdSource"), "patientId", Some("conceptCodeName"), Some("conceptCode"),
    Some("observerCodeSource"), "observerCode", "startDate", Some("modifierCode"), "valueTypeCode", Some("tvalChar"),
    Some("nvalNum"), Some("valueFlagCode"), Some("unitsCode"), Some("endDate"), Some("locationCodeName"),
    Some("locationCode"), List(new ParamResponse("someParam1", "someColumn1", "someValue1")))
  val observation2 = ObservationResponse(Some("eventIdSource"), "eventId", Some("patientIdSource"), "patientId", Some("conceptCodeName"), Some("conceptCode"),
    Some("observerCodeSource"), "observerCode", "startDate", Some("modifierCode"), "valueTypeCode", Some("tvalChar"),
    Some("nvalNum"), Some("valueFlagCode"), Some("unitsCode"), Some("endDate"), Some("locationCodeName"),
    Some("locationCode"), List(new ParamResponse("someParam2", "someColumn2", "someValue2")))
  val pdoResponse = new ReadPdoResponse(List(event1, event2), List(patient1, patient2), List(observation1, observation2))

  @Test
  def testToI2b2() = {
    val xml1 = pdoResponse.toI2b2
    val xml2 = response


    println(xml1 == xml2)
    println(xml1.diff(xml2))


  }

  @Test
  def testToXml() = {

  }


  @Test
  def testFromI2b2(): Unit = {
    val fromI2b2 = ReadPdoResponse.fromI2b2(response)
    fromI2b2.patients.size should equal(2)

    for (p <- fromI2b2.patients) {
      p.params.size should equal(2)
      p.patientId should not equal ""
      for (param <- p.params) {
        param.value should not equal ("")
        param.column should not equal ("")
        param.name should not equal ("")
      }
    }


  }

  def messageBody = XmlUtil.stripWhitespace(<message_body>
    <ns3:response xsi:type="ns3:patient_data_responseType" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <ns2:patient_data>
        <ns2:event_set>
          <event>
            <event_id>789012</event_id>
            <patient_id>1000000001</patient_id>
            <param name="vital_status_cd" column="vital_status_cd">N</param>
            <param name="birth_date" column="birth_date">1985-11-17T00:00:00.000-05:00</param>
            <start_date>2011-01-29T00:00:00.000-05:00</start_date>
            <end_date>2011-01-29T00:00:00.000-05:00</end_date>
          </event>
          <event>
            <event_id>123456</event_id>
            <patient_id>1000000001</patient_id>
            <param name="vital_status_cd" column="vital_status_cd">N</param>
            <param name="birth_date" column="birth_date">1985-11-17T00:00:00.000-05:00</param>
          </event>
        </ns2:event_set>
        <ns2:patient_set>
          <patient>
            <patient_id>1000000001</patient_id>
            <param name="vital_status_cd" column="vital_status_cd">N</param>
            <param name="birth_date" column="birth_date">1985-11-17T00:00:00.000-05:00</param>
          </patient>
          <patient>
            <patient_id>1000000002</patient_id>
            <param name="vital_status_cd" column="vital_status_cd">N</param>
            <param name="birth_date" column="birth_date">1966-08-29T00:00:00.000-04:00</param>
          </patient>
        </ns2:patient_set>
        <ns2:observation_set>
          <observation>
            <event_id source="eventIdSource">eventId</event_id>
            <patient_id source="patientIdSource">patientId</patient_id>
            <concept_cd name="conceptCodeName">conceptCode</concept_cd>
            <observer_cd source="observerCodeSource">observerCode</observer_cd>
            <start_date>startDate</start_date>
            <modifier_cd>modifierCode</modifier_cd>
            <valuetype_cd>valueTypeCode</valuetype_cd>
            <nval_num>nvalNum</nval_num>
            <valueflag_cd>valueFlagCode</valueflag_cd>
            <end_date>endDate</end_date>
            <location_cd name="locationCodeName">locationCode</location_cd>
            <param column="someColumn1" name="someParam1">someValue1</param>
          </observation>
          <observation>
            <event_id source="eventIdSource">eventId</event_id>
            <patient_id source="patientIdSource">patientId</patient_id>
            <concept_cd name="conceptCodeName">conceptCode</concept_cd>
            <observer_cd source="observerCodeSource">observerCode</observer_cd>
            <start_date>startDate</start_date>
            <modifier_cd>modifierCode</modifier_cd>
            <valuetype_cd>valueTypeCode</valuetype_cd>
            <nval_num>nvalNum</nval_num>
            <valueflag_cd>valueFlagCode</valueflag_cd>
            <end_date>endDate</end_date>
            <location_cd name="locationCodeName">locationCode</location_cd>
            <param column="someColumn1" name="someParam1">someValue1</param>
          </observation>
        </ns2:observation_set>
      </ns2:patient_data>
    </ns3:response>
  </message_body>)

  def xml = XmlUtil.stripWhitespace(
    <PdoResponse>
      <events>
        <event>
          <event_id>789012</event_id>
          <patient_id>1000000001</patient_id>
          <param name="vital_status_cd" column="vital_status_cd">N</param>
          <param name="birth_date" column="birth_date">1985-11-17T00:00:00.000-05:00</param>
          <start_date>2011-01-29T00:00:00.000-05:00</start_date>
          <end_date>2011-01-29T00:00:00.000-05:00</end_date>
        </event>
        <event>
          <event_id>123456</event_id>
          <patient_id>1000000001</patient_id>
          <param name="vital_status_cd" column="vital_status_cd">N</param>
          <param name="birth_date" column="birth_date">1985-11-17T00:00:00.000-05:00</param>
        </event>
      </events>
      <patients>
        <patient>
          <patient_id>1000000001</patient_id>
          <param name="vital_status_cd" column="vital_status_cd">N</param>
          <param name="birth_date" column="birth_date">1985-11-17T00:00:00.000-05:00</param>
        </patient>
        <patient>
          <patient_id>1000000002</patient_id>
          <param name="vital_status_cd" column="vital_status_cd">N</param>
          <param name="birth_date" column="birth_date">1966-08-29T00:00:00.000-04:00</param>
        </patient>
      </patients>
      <observations>
        <observation>
          <event_id source="eventIdSource">eventId</event_id>
          <patient_id source="patientIdSource">patientId</patient_id>
          <concept_cd name="conceptCodeName">conceptCode</concept_cd>
          <observer_cd source="observerCodeSource">observerCode</observer_cd>
          <start_date>startDate</start_date>
          <modifier_cd>modifierCode</modifier_cd>
          <valuetype_cd>valueTypeCode</valuetype_cd>
          <nval_num>nvalNum</nval_num>
          <valueflag_cd>valueFlagCode</valueflag_cd>
          <end_date>endDate</end_date>
          <location_cd name="locationCodeName">locationCode</location_cd>
          <param column="someColumn1" name="someParam1">someValue1</param>
        </observation>
        <observation>
          <event_id source="eventIdSource">eventId</event_id>
          <patient_id source="patientIdSource">patientId</patient_id>
          <concept_cd name="conceptCodeName">conceptCode</concept_cd>
          <observer_cd source="observerCodeSource">observerCode</observer_cd>
          <start_date>startDate</start_date>
          <modifier_cd>modifierCode</modifier_cd>
          <valuetype_cd>valueTypeCode</valuetype_cd>
          <nval_num>nvalNum</nval_num>
          <valueflag_cd>valueFlagCode</valueflag_cd>
          <end_date>endDate</end_date>
          <location_cd name="locationCodeName">locationCode</location_cd>
          <param column="someColumn2" name="someParam2">someValue2</param>
        </observation>
      </observations>
    </PdoResponse>)

  def makePdoResponse(numberOfObservations: Int): ReadPdoResponse = {
    val fromXml = ReadPdoResponse.fromXml(xml)

    val newObs = for (i <- 1 to numberOfObservations)
    yield (ObservationResponse.fromXml(XmlUtil.stripWhitespace(<observation>
        <event_id source="eventIdSource">eventId</event_id>
        <patient_id source="patientIdSource">patientId</patient_id>
        <concept_cd name="conceptCodeName">conceptCode</concept_cd>
        <observer_cd source="observerCodeSource">observerCode</observer_cd>
        <start_date>startDate</start_date>
        <modifier_cd>modifierCode</modifier_cd>
        <valuetype_cd>valueTypeCode</valuetype_cd>
        <nval_num>nvalNum</nval_num>
        <valueflag_cd>valueFlagCode</valueflag_cd>
        <end_date>endDate</end_date>
        <location_cd name="locationCodeName">locationCode</location_cd>
        <param column="someColumn1" name="someParam1">someValue1</param>
      </observation>)))

    return new ReadPdoResponse(fromXml.events, fromXml.patients, newObs)

  }


  @Test
  def testFromXml(): Unit = {
    val fromXml = ReadPdoResponse.fromXml(xml)
    fromXml.patients.size should equal(2)

    for (p <- fromXml.patients) {
      p.params.size should equal(2)
      p.patientId should not equal ""
      for (param <- p.params) {
        param.value should not equal ("")
        param.column should not equal ("")
        param.name should not equal ("")
      }
    }
  }

  def testXMLSerializationSpeed(observationSize: Int) {
    var xmlAvg: Long = 0

    var fromXml: ReadPdoResponse = null

    val newXML = makePdoResponse(observationSize).toXml

    println("Bytes " + newXML.toString().getBytes.length)
    for (a <- 1 to 10) {
      System.gc()
      val xmlStart: Long = System.currentTimeMillis()
      fromXml = ReadPdoResponse.fromXml(newXML)

      val xmlEnd: Long = System.currentTimeMillis()
      xmlAvg = xmlAvg + (xmlEnd - xmlStart)
    }

    println("Bytes " + newXML.toString().getBytes().length)
    println("Total time to deserialize " + observationSize + " observations, 50 times:" + xmlAvg)

  }



  def testBinarySerializeSpeed(observationSize: Int): Unit = {


    var reconstituted: ReadPdoResponse = null
    val string = makePdoResponse(observationSize).serializeToBase64Binary()


    var avg: Long = 0
    for (a <- 1 to 10) {
      System.gc()
      val start = System.currentTimeMillis()

      reconstituted = ReadPdoResponse.fromBinaryBase64string(string)
      val end = System.currentTimeMillis()
      avg = avg + (end - start)
    }

    println("Bytes " + string.getBytes.length)
    println("Total time to deserialize " + observationSize + " observations, 50 times:" + avg)



    reconstituted.toXml

  }


  def compareSpeeds(): Unit = {
    testBinarySerializeSpeed(500000)
    testXMLSerializationSpeed(500000)

  }




}


