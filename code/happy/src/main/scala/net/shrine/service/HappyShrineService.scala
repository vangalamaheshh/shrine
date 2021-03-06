package net.shrine.service

import java.io.StringWriter
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.asScalaSetConverter
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.xml.NodeSeq
import scala.xml.XML
import org.spin.client.SpinClient
import org.spin.message.Failure
import org.spin.message.QueryInfo
import org.spin.message.Result
import org.spin.message.ResultSet
import org.spin.message.serializer.Stringable
import org.spin.node.DefaultQueries
import org.spin.node.actions.discovery.DiscoveryCriteria
import org.spin.node.actions.discovery.DiscoveryResult
import org.spin.tools.JAXBUtils
import org.spin.tools.config.ConfigTool
import org.spin.tools.config.EndpointConfig
import org.spin.tools.config.RoutingTableConfig
import org.spin.tools.crypto.PKCryptor
import org.spin.tools.crypto.PKITool
import org.spin.tools.crypto.XMLSignatureUtil
import org.spin.tools.crypto.signature.CertID
import org.spin.tools.crypto.signature.Identity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.xml.bind.JAXBContext
import net.shrine.adapter.dao.AdapterDao
import net.shrine.broadcaster.dao.AuditDao
import net.shrine.config.HappyShrineConfig
import net.shrine.config.HiveCredentials
import net.shrine.i2b2.protocol.pm.GetUserConfigurationRequest
import net.shrine.i2b2.protocol.pm.HiveConfig
import net.shrine.protocol.AuthenticationInfo
import net.shrine.protocol.BroadcastMessage
import net.shrine.protocol.CrcRequestType
import net.shrine.protocol.Credential
import net.shrine.protocol.ResultOutputType
import net.shrine.protocol.RunQueryRequest
import net.shrine.protocol.query.OccuranceLimited
import net.shrine.protocol.query.QueryDefinition
import net.shrine.protocol.query.Term
import net.shrine.util.HttpClient
import net.shrine.util.Versions
import net.shrine.util.XmlUtil
import org.spin.tools.crypto.Envelope

/**
 * @author Bill Simons
 * @date 6/20/11
 * @link http://cbmi.med.harvard.edu
 * @link http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @link http://www.gnu.org/licenses/lgpl.html
 */
@Service
class HappyShrineService @Autowired() (
    config: HappyShrineConfig,
    hiveCredentials: HiveCredentials,
    spinClient: SpinClient,
    auditDao: AuditDao,
    adapterDao: AdapterDao,
    httpClient: HttpClient,
    endpointConfig: EndpointConfig) extends HappyShrineRequestHandler {

  override def keystoreReport: String = {
    val keystoreConfig = ConfigTool.loadKeyStoreConfig
    val pki: PKITool = PKITool.getInstance
    val certId: CertID = pki.getMyX509.getCertID
    XmlUtil.stripWhitespace {
      <keystoreReport>
        <configFile>{ keystoreConfig.getFile.toURI }</configFile>
        <certId>
          <name>{ certId.getName }</name>
          <serial>{ certId.getSerial }</serial>
        </certId>
        <importedCerts>
          {
            pki.getImportedCertsCopy.asScala.map { cert =>
              <cert>
                <name>{ cert.getCertID.getName }</name>
                <serial>{ cert.getCertID.getSerial.toString }</serial>
              </cert>
            }
          }
        </importedCerts>
      </keystoreReport>
    }.toString
  }

  override def routingReport: String = {
    val marshaller = JAXBContext.newInstance(classOf[RoutingTableConfig]).createMarshaller

    marshaller.setProperty("com.sun.xml.bind.xmlDeclaration", false);

    val sWriter = new StringWriter

    marshaller.marshal(ConfigTool.loadRoutingTableConfig, sWriter)

    sWriter.toString
  }

  override def hiveReport: String = {
    val pmRequest = GetUserConfigurationRequest(hiveCredentials.domain, hiveCredentials.username, hiveCredentials.password)

    val responseXml = httpClient.post(pmRequest.toI2b2String, config.pmEndpoint)

    HiveConfig.fromI2b2(responseXml).toXmlString
  }

  private def invalidSpinConfig = XmlUtil.stripWhitespace {
    <spin>
      <properlyConnected>false</properlyConnected>
      <error>peer group to query is not defined in routing table</error>
    </spin>
  }.toString

  private[service] def partitionSpinResults(resultSet: ResultSet): (Seq[Result], Seq[Failure]) = {
    val results = resultSet.getResults.asScala.toSeq

    val failures = resultSet.getFailures.asScala.toSeq

    (results, failures)
  }
  
  private lazy val cryptor = new PKCryptor
  
  private def decryptSpinResult(spinResultPayload: Envelope): String = {
    if(spinResultPayload.isEncrypted) { cryptor.decrypt(spinResultPayload) }
    else { spinResultPayload.getData }
  }

  private[service] def generateSpinReport(routingTable: RoutingTableConfig): String = {
    val peerGroupOption = routingTable.getPeerGroups.asScala.find(_.getGroupName == config.broadcasterPeerGroupToQuery)

    if (peerGroupOption.isEmpty) { invalidSpinConfig }
    else {
      val identity: Identity = XMLSignatureUtil.getDefaultInstance.sign(new Identity("happy", "happy"))

      import scala.concurrent.duration._

      implicit def jaxbTypesAreStringable[T]: Stringable[T] = Stringable.jaxb[T]

      val resultSet = Await.result(spinClient.query(DefaultQueries.Discovery.queryType, DiscoveryCriteria.Instance), 1.minute)

      val expectedCount = peerGroupOption.get.getChildren.size + 1 //add one to include self

      val (results, failures) = partitionSpinResults(resultSet)

      XmlUtil.stripWhitespace {
        <spin>
          <properlyConnected>{ !resultSet.getFailures.isEmpty }</properlyConnected>
          <expectedNodeCount>{ expectedCount }</expectedNodeCount>
          <actualNodeCount>{ resultSet.getResults.size }</actualNodeCount>
          <failureCount>{ resultSet.getFailures.size }</failureCount>
          {
            

            results.map { spinResult =>
              val xmlString = decryptSpinResult(spinResult.getPayload)
              
              val discoveryResult = unmarshal[DiscoveryResult](xmlString)
              
              <node>
                <name>{ discoveryResult.getNodeConfig.getNodeName }</name>
                <url>{ discoveryResult.getNodeURL }</url>
              </node>
            }
          }
          {
            failures.map(toXml)
          }
        </spin>
      }.toString
    }
  }

  //NB: Use JAXB for Spin JAXBable types, because it's easy and it works
  private def unmarshal[T: Manifest](xml: String): T = JAXBUtils.unmarshal(xml, manifest[T].runtimeClass.asInstanceOf[Class[T]])

  override def spinReport: String = {
    val routingTable: RoutingTableConfig = ConfigTool.loadRoutingTableConfig

    generateSpinReport(routingTable)
  }

  private def newRunQueryRequest: RunQueryRequest = {
    val queryDefinition = QueryDefinition("PDD", OccuranceLimited(1, Term(config.adapterStatusQuery)))

    RunQueryRequest(
      "happyProject",
      180000,
      AuthenticationInfo("happyDomain", "happy", Credential("", false)),
      BroadcastMessage.Ids.next,
      "",
      Set(ResultOutputType.PATIENT_COUNT_XML),
      queryDefinition)
  }

  override def adapterReport: String = {
    if (!config.isAdapter) {
      XmlUtil.stripWhitespace {
        <adapter>
          <isAdapter>false</isAdapter>
        </adapter>
      }.toString
    } else {
      val identity = XMLSignatureUtil.getDefaultInstance.sign(new Identity("happy", "happy"))
      val queryInfo = new QueryInfo("LOCAL", identity, CrcRequestType.QueryDefinitionRequestType.name, endpointConfig)
      val req = newRunQueryRequest
      val message = BroadcastMessage(req.networkQueryId, req)

      import scala.concurrent.duration._

      val queryType = CrcRequestType.QueryDefinitionRequestType.name

      val resultSet = Await.result(spinClient.query(queryType, message), 1.minute)

      val (results, failures) = partitionSpinResults(resultSet)

      XmlUtil.stripWhitespace {
        <adapter>
          <isAdapter>true</isAdapter>
          {
            results.map { result =>
              <result>
                <description>{ result.getDescription }</description>
                <payload>
                  {
                    val decryptedPayload = decryptSpinResult(result.getPayload)
                    
                    XML.loadString(decryptedPayload)
                  }
                </payload>
              </result>
            }
          }
          {
            failures.map(toXml)
          }
        </adapter>
      }.toString
    }
  }

  private[service] def toXml(failure: Failure): NodeSeq = {
    <failure>
      <origin>{ failure.getDescription }</origin>
      <description>{ failure.getDescription }</description>
    </failure>
  }

  override def auditReport: String = {
    val recentEntries = auditDao.findRecentEntries(10)
    XmlUtil.stripWhitespace {
      <recentAuditEntries>
        {
          recentEntries map { entry =>
            <entry>
              <id>{ entry.id }</id>
              <time>{ entry.time }</time>
              <username>{ entry.username }</username>
            </entry>
          }
        }
      </recentAuditEntries>
    }.toString
  }

  override def queryReport: String = {
    val recentQueries = adapterDao.findRecentQueries(10)

    XmlUtil.stripWhitespace {
      <recentQueries>
        {
          recentQueries.map { query =>
            <query>
              <id>{ query.networkId }</id>
              <date>{ query.dateCreated }</date>
              <name>{ query.name }</name>
            </query>
          }
        }
      </recentQueries>
    }.toString
  }

  override def versionReport: String = XmlUtil.stripWhitespace {
    <versionInfo>
      <shrineVersion>{ Versions.version }</shrineVersion>
      <scmRevision>{ Versions.scmRevision }</scmRevision>
      <scmBranch>{ Versions.scmBranch }</scmBranch>
      <buildDate>{ Versions.buildDate }</buildDate>
    </versionInfo>
  }.toString

  override def all: String = {
    new StringBuilder("<all>")
      .append(versionReport)
      .append(keystoreReport)
      .append(routingReport)
      .append(hiveReport)
      .append(spinReport)
      .append(adapterReport)
      .append(auditReport)
      .append(queryReport)
      .append("</all>").toString
  }
}
