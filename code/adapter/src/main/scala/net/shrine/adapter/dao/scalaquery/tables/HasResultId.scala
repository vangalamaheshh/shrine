package net.shrine.adapter.dao.scalaquery.tables

import org.scalaquery.ql._
import org.scalaquery.ql.TypeMapper._
import org.scalaquery.ql.extended.{ExtendedTable => Table}

/**
 * @author clint
 * @date Oct 12, 2012
 */
trait HasResultId { self: Table[_] =>
  def resultId = column[Int]("RESULT_ID", O.NotNull)
  
  import ForeignKeyAction.{ NoAction, Cascade }
  
  def resultIdFk = foreignKey("ResultId_FK", resultId, QueryResults)(targetColumns = _.id, onUpdate = NoAction, onDelete = Cascade)
}