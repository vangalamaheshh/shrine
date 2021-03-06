package net.shrine.aggregation
import net.shrine.protocol.ShrineResponse
import net.shrine.aggregation.BasicAggregator.{Invalid, Error, Valid}
import net.shrine.protocol.QueryResult

/**
 *
 * @author Clint Gilbert
 * @date Sep 16, 2011
 *
 * @link http://cbmi.med.harvard.edu
 *
 * This software is licensed under the LGPL
 * @link http://www.gnu.org/licenses/lgpl.html
 *
 * Extends BasicAggregator to package Errors and Invalid responses into QueryResults
 * 
 * Needs to be an abstract class instead of a trait due to the view bound on T (: Manifest)
 */
abstract class PackagesErrorsAggregator[T <: ShrineResponse : Manifest](
    errorMessage: Option[String] = None, 
    invalidMessage: Option[String] = None) extends BasicAggregator[T] {
  
  private[aggregation] def makeErrorResult(error: Error): QueryResult = { 
    val Error(spinResultOption, errorResponse) = error
    
    val desc = (for {
      spinResult <- spinResultOption
      spinMetadata <- Option(spinResult.spinResultMetadata)
      description <- Option(spinMetadata.getDescription) 
    } yield description) orElse Option(errorResponse.errorMessage)
    
    QueryResult.errorResult(desc, errorMessage.getOrElse(errorResponse.errorMessage))
  }
  
  private[aggregation] def makeInvalidResult(invalid: Invalid): QueryResult = {
    val Invalid(spinResult, errorMessage) = invalid 
    
    val desc = Option(spinResult.spinResultMetadata.getDescription)
    
    QueryResult.errorResult(desc, invalidMessage.getOrElse(errorMessage))
  }
  
  private[aggregation] final override def makeResponseFrom(validResponses: Seq[Valid[T]], errorResponses: Seq[Error], invalidResponses: Seq[Invalid]): ShrineResponse = {
    makeResponse(validResponses, errorResponses.map(makeErrorResult), invalidResponses.map(makeInvalidResult))
  }
  
  private[aggregation] def makeResponse(validResponses: Seq[Valid[T]], errorResponses: Seq[QueryResult], invalidResponses: Seq[QueryResult]): ShrineResponse
}
