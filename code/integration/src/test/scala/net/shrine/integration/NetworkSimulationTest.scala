package net.shrine.integration

import scala.collection.JavaConverters._
import org.junit.Test
import org.scalatest.junit.ShouldMatchersForJUnit
import org.spin.client.Credentials
import org.spin.client.AbstractSpinClient
import org.spin.client.LocalSpinClient
import org.spin.client.SpinClient
import org.spin.client.SpinClientConfig
import org.spin.identity.local.DummyIdentityService
import org.spin.message.AckNack
import org.spin.message.Result
import org.spin.message.StatusCode
import org.spin.node.DefaultQueries
import org.spin.node.NodeOperationFactory
import org.spin.node.NodeRegistry
import org.spin.node.RoutingTableConfigSources
import org.spin.node.SpinNodeImpl
import org.spin.node.cache.QueryNotFoundException
import org.spin.node.connector.NodeConnector
import org.spin.node.connector.NodeConnectorSource
import org.spin.tools.Durations
import org.spin.tools.JAXBUtils
import org.spin.tools.RandomTool
import org.spin.tools.config.EndpointConfig
import org.spin.tools.config.EndpointType
import org.spin.tools.config.NodeConfig
import org.spin.tools.config.PeerGroupConfig
import org.spin.tools.config.RoutingTableConfig
import org.spin.tools.crypto.PKITool
import org.spin.tools.crypto.signature.CertID
import junit.framework.TestCase
import net.shrine.protocol.AuthenticationInfo
import net.shrine.protocol.BroadcastMessage
import net.shrine.protocol.Credential
import net.shrine.protocol.ReadPreviousQueriesRequest
import net.shrine.util.Loggable
import net.shrine.service.ShrineService
import net.shrine.broadcaster.dao.AuditDao
import java.util.Date
import net.shrine.broadcaster.dao.model.AuditEntry
import net.shrine.authorization.AllowsAllAuthorizationService
import net.shrine.protocol.RunQueryRequest
import net.shrine.protocol.ReadPreviousQueriesResponse
import org.spin.client.SpinClient
import org.spin.message.QueryInfo
import org.spin.message.serializer.BasicSerializer
import org.spin.message.QueryInput
import org.spin.message.ResultSet
import org.spin.tools.crypto.signature.Identity
import org.spin.identity.AlwaysCertifiesIdentityService
import net.shrine.adapter.query.ShrineQueryActionMap
import org.spin.tools.config.QueryTypeConfig
import org.spin.node.OnlineNodeState
import net.shrine.adapter.ReadPreviousQueriesAdapter
import net.shrine.adapter.dao.MockAdapterDao
import org.spin.node.QueryAction
import net.shrine.adapter.dao.model.ShrineQuery
import net.shrine.adapter.dao.model.ShrineQuery
import net.shrine.protocol.query.Term
import net.shrine.util.Util
import scala.concurrent.Future
import scala.concurrent.blocking
import scala.concurrent.Await
import org.spin.message.serializer.Stringable
import net.shrine.broadcaster.spin.SpinBroadcastService
import org.spin.client.SpinClientDefaults

/**
 * @author Clint Gilbert
 *
 *         Jun 9, 2008
 *
 *         Intelligent Health Lab at the Childrens Hospital Informatics Program
 *         at the Harvard-MIT division of Health Sciences Technology
 * @link http://www.chip.org/ihl
 *
 *       All works licensed by the Lesser GPL
 * @link http://www.gnu.org/licenses/lgpl.html
 *
 *       Does a fully end-to-end test of a single query on an in-memory network
 *       of 8 nodes, like this:
 *
 *       Root
 *       / | \
 *      A  B  C
 *     / \
 *    D   E
 *   / \
 *  F   G
 *
 *       and a multithreaded stress test of a single node with no children.
 *
 *       setUp() builds the in-memory network of nodes, programatically creating
 *       each one's routing table with appropriate EndPointCounfigs to create
 *       the structure shown above.
 *
 *       NST doesn't require anything to be in /opt/spin/main/conf or
 *       /opt/spin/test/conf. Everything the test needs is in
 *       src/test/resources. This is how all Spin unit tests should be - they
 *       should run on checkout with no user-specific config setup.
 *
 *       For both tests, an audit query is performed, since that's a querytype
 *       that's present in Spin base - we can't use an extension-specific query.
 *       Audit queries are also good because they don't return a ton of data,
 *       but they do return a bunch. I wanted the nodes to do a decent amount of
 *       work, but not so much that the test took too long to run.
 *
 *       The end-to-end test takes advantage of the fact that the mechanism for
 *       communicating between Nodes is abstracted. Instead of making SOAP calls
 *       via JAXWSNodeConnectors, the nodes talk to each other using
 *       LocalNodeConnectors (they're all in the same JVM). Everything works as
 *       it would in the real world queries get broadcast, acks get sent,
 *       results come back, all asynchronously in an aggressively threaded way.
 *       All of Node's subsystems get exercised: broadcasting, acking,
 *       aggregating, querying, logging, etc, etc.
 *
 *       Once the query is made, the NST tests that a result came back to the
 *       root, the results can be unmarshalled into LogEntries (I think), the
 *       query didn't time out, and that the root received the right number of
 *       results and acks. It also digs into each node farther down the
 *       hierarchy, checking for the right number of acks and result responses.
 *
 *       The stress test just hammers a single node, with (right now) 50 threads
 *       making 3 queries each. It tries to check that all the queries
 *       succeeded, nothing timed out, etc.
 *
 *       Everything this test needs to run is present in
 *       integration/src/test/resources
 *
 *       TODO: test querying a single node using the local peergroup
 */
final class NetworkSimulationTest extends TestCase with ShouldMatchersForJUnit with Loggable {

  private var nodes: LocalNodeMap[SpinNodeImpl] = new LocalNodeMap(Map.empty)

  private var myCertID: CertID = _

  private val idService = AlwaysCertifiesIdentityService.Instance

  private val domain = DummyIdentityService.testEntry.getDomain

  private val username = DummyIdentityService.testEntry.getUsername

  private val password = DummyIdentityService.testEntryPassword

  private val requestorIdentity = idService.certify(domain, username, password)

  private object PeerGroupName {
    val Test = "TEST"
  }

  private val localNodeConnectorSource: NodeConnectorSource = new NodeConnectorSource {
    override def getNodeConnector(endpoint: EndpointConfig, maxWaitTime: Long): NodeConnector = {
      require(endpoint.getEndpointType == EndpointType.Local)

      nodes.getNodeConnector(NodeName.valueOf(endpoint.getAddress).get)
    }
  }

  private lazy val init: Unit = {
    info("Adding shutdown hook")

    // For tests only, blunt-instrument method to ensure all nodes get shut down
    // when the JVM exits.
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run() = NodeRegistry.shutDown()
    }))
  }

  override protected def setUp() {
    super.setUp()

    val x = init

    myCertID = PKITool.getInstance.getMyCertID

    // Set up local (in-JVM) inter-node communications

    NodeOperationFactory.addMapping(EndpointType.Local, localNodeConnectorSource)

    // Now wire up the network
    nodes += (NodeName.ROOT -> makeNode(NodeName.ROOT, None, Seq(NodeName.A, NodeName.B, NodeName.C)))

    // Root's children
    nodes += (NodeName.A -> makeNode(NodeName.A, Some(NodeName.ROOT), Seq(NodeName.D, NodeName.E)))
    nodes += (NodeName.B -> makeNode(NodeName.B, Some(NodeName.ROOT), Seq.empty))
    nodes += (NodeName.C -> makeNode(NodeName.C, Some(NodeName.ROOT), Seq.empty))

    // A's children
    nodes += (NodeName.D -> makeNode(NodeName.D, Some(NodeName.A), Seq(NodeName.F, NodeName.G)))
    nodes += (NodeName.E -> makeNode(NodeName.E, Some(NodeName.A), Seq.empty))

    // D's children
    nodes += (NodeName.F -> makeNode(NodeName.F, Some(NodeName.D), Seq.empty))
    nodes += (NodeName.G -> makeNode(NodeName.G, Some(NodeName.D), Seq.empty))

    // X is standalone
    nodes += (NodeName.X -> makeNode(NodeName.X, None, Seq.empty))
  }

  override protected def tearDown() {
    super.tearDown()

    // Shut down any nodes created by tests, so they may be GC'd
    NodeRegistry.shutDown()
  }

  private def countNodesInTree(root: NodeName): Int = {
    if (nodes.contains(root)) {
      1 + (for {
        childEndpoint <- nodes.get(root).getRoutingTable.get(PeerGroupName.Test).getChildren.asScala
        childNodeName <- NodeName.valueOf(childEndpoint.getAddress)
      } yield {
        countNodesInTree(childNodeName)
      }).sum
    } else { 0 }
  }

  private def doAckNackTest(ack: AckNack, expectedStatusCodes: Set[StatusCode]) {
    ack should not be (null)

    ack.getQueryId should not be (null)

    ack.getStatuses.asScala.toSet should equal(expectedStatusCodes)
  }

  @Test
  def testSimulatedNetworkBubbleUp = doTestSimluatedNetwork(None, NodeName.ROOT)

  @Test
  def testSimulatedNetworkCentralAggregator = doTestSimluatedNetwork(Some(NodeName.ROOT), NodeName.ROOT)

  private def doTestSimluatedNetwork(centralAggregator: Option[NodeName], rootNodeName: NodeName) {
    //supply serializer that does .toXml

    val rootEndpoint = EndpointConfig.local(rootNodeName.name)
    
    val rootNode = nodes.get(rootNodeName)

    val executionContext = SpinClientDefaults.forkJoinExecutionContext
    
    val client = new SavesQueryIdsSpinClient(new LocalSpinClient(makeSpinClientConfig(rootEndpoint, centralAggregator), rootEndpoint -> rootNode)(executionContext))

    val broadcastService = new SpinBroadcastService(client, executionContext)
    
    import scala.concurrent.duration._
    
    val shrineService = new ShrineService( 
      MockAuditDao,
      new AllowsAllAuthorizationService,
      true,
      broadcastService,
      1.hour)

    val req = ReadPreviousQueriesRequest("SHRINE", 10000L, AuthenticationInfo(domain, username, Credential(password, false)), username, 100)

    val resp = shrineService.readPreviousQueries(req).asInstanceOf[ReadPreviousQueriesResponse]

    val queryId = client.queryId.get

    val numResultsReceived = resp.queryMasters.size

    val numNodesInTree = countNodesInTree(rootNodeName)

    val expectedNumResponses = countQueryableNodes(getNodesInSubtree(rootNodeName))

    //"Wrong number of responses at root"
    numResultsReceived should equal(expectedNumResponses)

    //"Query " + queryId + " should have been deleted from the root node's ResultStore"
    intercept[QueryNotFoundException] {
      nodes.get(rootNodeName).getResultNoDelete(queryId, requestorIdentity)
    }

    // Assert correct number of responses at each node
    /*        Root
     *       / | \
     *      A  B  C
     *     / \
     *    D   E
     *   / \
     *  F   G
     * 
     */
    val isBubbleUp = centralAggregator.isEmpty

    //All nodes are aggregators, so they should at least have their own results; 
    //If we're in bubble-up mode, results from downstream should have passed through 
    //and been stored
    doExpectedResponsesTest(nodes.get(NodeName.A), queryId, if (isBubbleUp) 5 else 1)
    doExpectedResponsesTest(nodes.get(NodeName.B), queryId, 1)
    doExpectedResponsesTest(nodes.get(NodeName.C), queryId, 1)
    doExpectedResponsesTest(nodes.get(NodeName.D), queryId, if (isBubbleUp) 3 else 1)
    doExpectedResponsesTest(nodes.get(NodeName.E), queryId, 1)
    doExpectedResponsesTest(nodes.get(NodeName.F), queryId, 1)
    doExpectedResponsesTest(nodes.get(NodeName.G), queryId, 1)
  }

  private final class SavesQueryIdsSpinClient(delegate: AbstractSpinClient) extends AbstractSpinClient(delegate.config, delegate.nodeConnectorSource, delegate.executionContext) {
    var queryId: Option[String] = None

    private def storeQueryId(f: => AckNack): AckNack = {
      val ack = f

      queryId = Option(ack.getQueryId)

      ack
    }

    override def query[T : Stringable](queryType: String, conditions: T, peerGroup: String, credentials: Credentials): Future[ResultSet] = {
      val identity = credentials.toIdentity(config.identityService)
      
      val ack = storeQueryId(send(queryType, conditions, peerGroup, identity))
      
      import scala.concurrent.ExecutionContext.Implicits.global
      
      Future { blocking { receive(ack.getQueryId, identity) } }.map(decryptIfNecessary)
    }
  }

  private object MockAuditDao extends AuditDao {
    override def addAuditEntry(
      time: Date,
      project: String,
      username: String,
      domain: String,
      queryText: String,
      queryTopic: String): Unit = ()

    override def findRecentEntries(limit: Int): Seq[AuditEntry] = Nil

    override def inTransaction[T](f: => T): T = f
  }

  private def getNodesInSubtree(root: NodeName): Iterable[SpinNodeImpl] = {
    if (root == null) { Seq.empty }
    else {
      val rootNode = nodes.get(root)

      rootNode +: (for {
        childEndpoint <- rootNode.getRoutingTable.get(PeerGroupName.Test).getChildren.asScala
        childNodeName <- NodeName.valueOf(childEndpoint.getAddress).toSeq
        node <- getNodesInSubtree(childNodeName)
      } yield node)
    }
  }

  private def countQueryableNodes(nodes: Iterable[SpinNodeImpl]): Int = nodes.count(_.getNodeConfig.isQueryable)

  private def doExpectedResponsesTest(node: SpinNodeImpl, queryId: String, treeSize: Int) {
    val resultSet = node.getResultNoDelete(queryId, requestorIdentity)

    //"Mismatched number of children and responses for node " + node.getNodeID()

    resultSet.size should equal(treeSize)
  }

  private def makeSpinClientConfig(entryPoint: EndpointConfig): SpinClientConfig = makeSpinClientConfig(entryPoint, None)

  private def makeSpinClientConfig(entryPoint: EndpointConfig, rootAggregator: Option[NodeName]): SpinClientConfig = {
    val credentials = Credentials(requestorIdentity.getDomain, requestorIdentity.getUsername, "some-password")
    
    val result = SpinClientConfig.Default.
    				withMaxWaitTime(Durations.InMilliseconds.oneSecond * 60).
    				withPeerGroupToQuery(PeerGroupName.Test).
    				withCredentials(credentials).
    				withEntryPoint(entryPoint)

    rootAggregator match {
      case Some(aggregator) => result.withAggregatorEndpoint(rootAggregator.map(_.name).map(EndpointConfig.local))
      case None => result
    }
  }

  private def makeNode(nodeName: NodeName, parent: Option[NodeName], children: Iterable[NodeName]): SpinNodeImpl = {
    val isBroadcaster = !children.isEmpty

    val isAggregator = true

    val nodeConfig = makeNodeConfig(nodeName.name, isBroadcaster, isAggregator)

    val routingTable = makeRoutingTable(parent, children)

    val nodeId = RandomTool.randomCertID

    nodeId.setName(nodeName.name)

    import scala.collection.mutable

    val adapter = new ReadPreviousQueriesAdapter(new MockAdapterDao {
      override def findQueriesByUserAndDomain(domain: String, username: String, howMany: Int): Seq[ShrineQuery] = {
        import RandomTool._

        Seq(new ShrineQuery(randomInt, "local_" + randomInt, randomInt.toLong, randomString, username, domain, Term(randomString), Util.now))
      }
    })

    val adapterMap: mutable.Map[String, QueryAction[_]] = mutable.Map("UserRequestType" -> adapter)

    val queryActionMap = new ShrineQueryActionMap(adapterMap.asJava)

    val nodeState = new OnlineNodeState(nodeId, nodeConfig, RoutingTableConfigSources.withConfigObject(routingTable), queryActionMap)

    new SpinNodeImpl(nodeState)
  }

  private def toLocalEndpoint(nodeName: String): EndpointConfig = new EndpointConfig(EndpointType.Local, nodeName)

  private def toLocalEndpoint(nodeName: NodeName): EndpointConfig = toLocalEndpoint(nodeName.name)

  private def makeRoutingTable(parent: Option[NodeName], children: Iterable[NodeName]): RoutingTableConfig = {
    val peerGroup = new PeerGroupConfig(PeerGroupName.Test, parent.map(toLocalEndpoint).orNull, children.map(toLocalEndpoint).toSeq.asJava)

    new RoutingTableConfig(peerGroup)
  }

  private def makeNodeConfig(nodeName: String, isBroadcaster: Boolean, isAggregator: Boolean): NodeConfig = {
    NodeConfig.Default.
      withNodeName(nodeName).
      withIsAuthenticator(false).
      withIsAggregator(isAggregator).
      withIsBroadcaster(isBroadcaster).
      withIsQueryable(true)
  }
}

