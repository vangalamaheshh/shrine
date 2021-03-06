package net.shrine.protocol.query

import scala.xml.NodeSeq
import javax.xml.datatype.XMLGregorianCalendar
import org.spin.tools.NetworkTime
import net.shrine.util.XmlUtil
import net.liftweb.json.JsonDSL._
import net.shrine.serialization.{ JsonUnmarshaller, JsonMarshaller, XmlMarshaller, XmlUnmarshaller }
import net.liftweb.json.JsonAST._
import net.shrine.util.Util
import scala.util.Try
import scala.util.Failure
import scala.util.Success

/**
 *
 * @author Clint Gilbert
 * @date Jan 24, 2012
 *
 * @link http://cbmi.med.harvard.edu
 *
 * This software is licensed under the LGPL
 * @link http://www.gnu.org/licenses/lgpl.html
 *
 * Classes to form expression trees representing Shrine queries
 */
sealed trait Expression extends XmlMarshaller with JsonMarshaller {
  def normalize: Expression = this

  def hasDirectI2b2Representation: Boolean

  def toExecutionPlan: ExecutionPlan
}

object Expression extends XmlUnmarshaller[Try[Expression]] with JsonUnmarshaller[Try[Expression]] {

  private def to[C <: ComposeableExpression[C]](make: (Expression*) => C): Seq[Expression] => C = make(_: _*)

  private val toOr = to(Or)
  private val toAnd = to(And)

  import Util.Tries.sequence
  
  def fromJson(json: JValue): Try[Expression] = {
    def dateFromJson(json: JValue): Try[XMLGregorianCalendar] = Try {
      json match {
        case JString(value) => NetworkTime.makeXMLGregorianCalendar(value)
        case _ => throw new Exception("Cannot parse json date" + json) //TODO some sort of unmarshalling exception
      }
    }

    json.children.head match {
      case JField("term", JString(value)) => Try(Term(value))
      case JField("query", JString(value)) => Try(Query(value))
      case JField("not", value) => fromJson(value).map(Not)
      case JField("and", value) => sequence(value.children.map(fromJson)).map(toAnd)
      case JField("or", value) => sequence(value.children.map(fromJson)).map(toOr)
      case JField("dateBounded", value) => {
        for {
          expr <- fromJson(value \ "expression")
          start = dateFromJson(value \ "start").toOption
          end = dateFromJson(value \ "end").toOption
        } yield DateBounded(start, end, expr)
      }
      case JField("occurs", value) => {
        val min = Try((value \ "min") match {
          case JInt(x) => x.intValue
          case x => throw new Exception("Cannot parse json: " + x.toString) //TODO some sort of unmarshalling exception
        })

        for {
          expr <- fromJson(value \ "expression")
          m <- min
        } yield OccuranceLimited(m, expr)
      }
      case x => Failure(new Exception("Cannot parse json: " + x.toString)) //TODO some sort of unmarshalling exception
    }
  }

  def fromXml(nodeSeq: NodeSeq): Try[Expression] = {
    def dateFromXml(dateString: String) = {
      if (dateString.trim.isEmpty) {
        None
      } else {
        Try(NetworkTime.makeXMLGregorianCalendar(dateString)).toOption
      }
    }

    val outerTag = nodeSeq.head

    nodeSeq.size match {
      case 0 => Try(Or())
      case _ => {
        val childTags = outerTag.child

        outerTag.label match {
          case "term" => Try(Term(outerTag.text))
          case "query" => Try(Query(outerTag.text))
          //childTags.head because only one child expr of <not> is allowed
          case "not" => fromXml(childTags.head).map(Not)
          case "and" => {
            sequence(childTags.map(fromXml)).map(toAnd)
          }
          case "or" => {
            sequence(childTags.map(fromXml)).map(toOr)
          }
          case "dateBounded" => {
            for {
              //drop(2) to lose <start> and <end>
              //childTags.drop(2).head because only one child expr of <dateBounded> is allowed
              expr <- fromXml(childTags.drop(2).head)
              start = dateFromXml((nodeSeq \ "start").text)
              end = dateFromXml((nodeSeq \ "end").text)
            } yield DateBounded(start, end, expr)
          }
          case "occurs" => {
            for {
              min <- Try((nodeSeq \ "min").text.toInt)
              //drop(1) to lose <min>
              //childTags.drop(2).head because only one child expr of <occurs> is allowed
              expr <- fromXml(childTags.drop(1).head)
            } yield OccuranceLimited(min, expr)
          }
          case _ => Failure(new Exception("Cannot parse xml: " + nodeSeq.toString)) //TODO some sort of unmarshalling exception
        }
      }
    }
  }
}

abstract class SimpleExpression(val value: String) extends Expression {
  override def hasDirectI2b2Representation = true

  override def toExecutionPlan = SimplePlan(this)

  def computeHLevel: Try[Int]
}

//NOTE - refactoring the field name value will break json deserialization for this case class
final case class Term(override val value: String) extends SimpleExpression(value) {
  override def toXml: NodeSeq = XmlUtil.stripWhitespace(<term>{ value }</term>)

  override def toJson: JValue = ("term" -> value)

  override def computeHLevel: Try[Int] = {
    //Super-dumb way: calculate nesting level by dropping prefix and counting \'s
    Try(value.drop("\\\\SHRINE\\SHRINE\\".length).count(_ == '\\'))
  }
}

final case class Query(localMasterId: String) extends SimpleExpression(Query.prefix + localMasterId) {
  override def toXml: NodeSeq = XmlUtil.stripWhitespace(<query>{ localMasterId }</query>)

  override def toJson: JValue = ("query" -> localMasterId)

  override def computeHLevel = Success(0)
}

object Query {
  val prefix = "masterid:"
  
  val prefixRegex = ("^" + prefix + "(.+?)").r
}

final case class Not(expr: Expression) extends Expression {
  def withExpr(newExpr: Expression) = if (newExpr eq expr) this else this.copy(expr = newExpr)

  override def toXml: NodeSeq = XmlUtil.stripWhitespace(<not>{ expr.toXml }</not>)

  override def toJson: JValue = ("not" -> expr.toJson)

  override def normalize = {
    expr match {
      //Collapse repeated Nots: Not(Not(e)) => e
      case Not(e) => e.normalize
      case _ => this.withExpr(expr.normalize)
    }
  }

  override def hasDirectI2b2Representation = expr.hasDirectI2b2Representation

  override def toExecutionPlan = SimplePlan(this.normalize)
}

trait HasSubExpressions extends Expression {
  val exprs: Seq[Expression]
}

abstract class ComposeableExpression[T <: ComposeableExpression[T]: Manifest](Op: (Expression*) => T, override val exprs: Expression*) extends HasSubExpressions {
  import ExpressionHelpers.is

  def containsA[E: Manifest] = exprs.exists(is[E])

  def ++(es: Iterable[Expression]): T = Op((exprs ++ es): _*)

  def merge(other: T): T = Op((exprs ++ other.exprs): _*)

  private[query] lazy val empty: T = Op()

  private[query] def toIterable(e: Expression): Iterable[Expression] = e match {
    case op: T if is[T](op) => op.exprs
    case _ => Seq(e)
  }

  override def normalize = {
    val result = exprs.map(_.normalize) match {
      case x if x.isEmpty => this
      case Seq(expr) => expr
      case es => es.foldLeft(empty)((accumulator, expr) => accumulator ++ toIterable(expr))
    }
    
    result match {
      case op: T if is[T](op) && op.containsA[T] => op.normalize
      case _ => result
    }
  }
}

final case class And(override val exprs: Expression*) extends ComposeableExpression[And](And, exprs: _*) {

  override def toString = "And(" + exprs.mkString(",") + ")"

  override def toXml: NodeSeq = XmlUtil.stripWhitespace(<and>{ exprs.map(_.toXml) }</and>)

  override def toJson: JValue = ("and" -> exprs.map(_.toJson))

  override def hasDirectI2b2Representation = exprs.forall(_.hasDirectI2b2Representation)

  override def toExecutionPlan: ExecutionPlan = {
    if (hasDirectI2b2Representation) {
      SimplePlan(this.normalize)
    } else {
      CompoundPlan.And(exprs.map(_.toExecutionPlan): _*).normalize
    }
  }
}

final case class Or(override val exprs: Expression*) extends ComposeableExpression[Or](Or, exprs: _*) {

  override def toString = "Or(" + exprs.mkString(",") + ")"

  override def toXml: NodeSeq = XmlUtil.stripWhitespace(<or>{ exprs.map(_.toXml) }</or>)

  override def toJson: JValue = ("or" -> exprs.map(_.toJson))

  import ExpressionHelpers.is

  override def hasDirectI2b2Representation: Boolean = exprs.forall(e => !is[And](e) && e.hasDirectI2b2Representation)

  override def toExecutionPlan: ExecutionPlan = {
    if (hasDirectI2b2Representation) {
      SimplePlan(this.normalize)
    } else {
      val (ands, notAnds) = exprs.partition(is[And])

      val andPlans = ands.map(_.toExecutionPlan)

      val andCompound = CompoundPlan.Or(andPlans: _*)

      if (notAnds.isEmpty) {
        andCompound
      } else {
        val notAndPlans = notAnds.map(_.toExecutionPlan)

        val consolidatedNotAndPlan = notAndPlans.reduce(_ or _)

        val components: Seq[ExecutionPlan] = andPlans.size match {
          case 1 => andPlans :+ consolidatedNotAndPlan
          case _ => if (ands.isEmpty) Seq(consolidatedNotAndPlan) else Seq(andCompound, consolidatedNotAndPlan)
        }

        val result = components match {
          case Seq(plan: CompoundPlan) => plan
          case _ => CompoundPlan.Or(components: _*)
        }

        result.normalize
      }
    }
  }
}

final case class DateBounded(start: Option[XMLGregorianCalendar], end: Option[XMLGregorianCalendar], expr: Expression) extends Expression {

  def withExpr(newExpr: Expression) = if (newExpr eq expr) this else this.copy(expr = newExpr)

  override def toXml: NodeSeq = XmlUtil.stripWhitespace(<dateBounded>
                                                          { start.map(x => <start>{ x }</start>).getOrElse(<start/>) }
                                                          { end.map(x => <end>{ x }</end>).getOrElse(<end/>) }
                                                          { expr.toXml }
                                                        </dateBounded>)

  override def toJson: JValue = ("dateBounded" ->
    ("start" -> start.map(_.toString)) ~
    ("end" -> end.map(_.toString)) ~
    ("expression" -> expr.toJson))

  override def normalize = {
    def normalize(date: Option[XMLGregorianCalendar]) = date.map(_.normalize)

    if (start.isEmpty && end.isEmpty) {
      expr.normalize
    } else {
      //NB: Dates are normalized to UTC.  I don't know if this is right, but it's what the existing webclient seems to do.
      val normalizedSubExpr = expr.normalize
      val normalizedStart = normalize(start)
      val normalizedEnd = normalize(end)

      DateBounded(normalizedStart, normalizedEnd, normalizedSubExpr)
    }
  }

  override def toExecutionPlan = SimplePlan(this.normalize)

  override def hasDirectI2b2Representation = expr.hasDirectI2b2Representation
}

final case class OccuranceLimited(min: Int, expr: Expression) extends Expression {
  require(min >= 1)

  def withExpr(newExpr: Expression) = if (newExpr eq expr) this else this.copy(expr = newExpr)

  override def toXml: NodeSeq = XmlUtil.stripWhitespace(<occurs>
                                                          <min>{ min }</min>
                                                          { expr.toXml }
                                                        </occurs>)

  override def toJson: JValue = (
    "occurs" -> ("min" -> min) ~
    ("expression" -> expr.toJson))

  override def normalize = if (min == 1) expr.normalize else this.withExpr(expr.normalize)

  override def toExecutionPlan = SimplePlan(this.normalize)

  override def hasDirectI2b2Representation = expr.hasDirectI2b2Representation
}