package net.shrine.broadcaster.dao

import java.util.Date

import scala.slick.session.Database
import scala.slick.session.Session

import org.junit.Test
import org.scalatest.junit.ShouldMatchersForJUnit
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.AbstractDependencyInjectionSpringContextTests

import net.shrine.broadcaster.dao.model.AuditEntry
import net.shrine.broadcaster.dao.slick.tables.Tables

/**
 * @author ??
 * @author Clint Gilbert
 * @date ??
 * Ported to Scala on Feb 28, 2012
 *
 */
final class AuditDaoTest extends AbstractDependencyInjectionSpringContextTests with ShouldMatchersForJUnit {

  @Autowired
  private var auditDao: AuditDao = _

  @Autowired
  private var tables: Tables = _

  @Autowired
  private var database: Database = _

  override protected def getConfigLocations: Array[String] = Array("classpath:testApplicationContext.xml")

  private val numDummyEntries = 20

  @Test
  def testGetRecentEntries = afterMakingTables {
    val limit = numDummyEntries / 2

    val entries = auditDao.findRecentEntries(limit)

    entries should not be (null)
    entries.isEmpty should be(false)
    entries.size should equal(limit)

    assertDateInDescendingOrder(entries)
  }

  private def assertDateInDescendingOrder(entries: Seq[AuditEntry]) {
    for (Seq(left, right) <- entries.sliding(2)) {
      (left.time.getTime > right.time.getTime) should be(true)
    }

    entries should equal(entries.sortBy(_.time.getTime).reverse)
  }

  private def afterMakingTables(f: => Any) {
    val t = tables
    import t.driver.Implicit._

    database.withTransaction { implicit session: Session =>
      try {
        tables.AuditEntries.ddl.create

        addDummyEntries()

        f
      } finally {
        tables.AuditEntries.ddl.drop
      }
    }
  }

  private def addDummyEntries() {
    for (i <- 0 until numDummyEntries) {
      addNewAuditEntry(i, new Date(1000 * i))
    }
  }

  private def addNewAuditEntry(i: Int, date: Date) {
    val project = "project" + i
    val username = "username" + i
    val domain = "domain" + i
    val queryText = "query" + i
    val queryTopic = "topic" + i

    auditDao.addAuditEntry(date, project, username, domain, queryText, queryTopic)

    val Seq(entry) = auditDao.findRecentEntries(1)

    entry.auditEntryId.asInstanceOf[AnyRef] should not be (null)
    entry.domain should equal(domain)
    entry.username should equal(username)
    entry.project should equal(project)
    entry.queryText should equal(queryText)
    entry.queryTopic should equal(queryTopic)
    entry.time.getTime should equal(date.getTime)
  }
}
