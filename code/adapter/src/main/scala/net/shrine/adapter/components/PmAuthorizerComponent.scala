package net.shrine.adapter.components

import net.shrine.util.HttpClient
import net.shrine.protocol.AuthenticationInfo
import net.shrine.i2b2.protocol.pm.User
import net.shrine.protocol.ErrorResponse
import net.shrine.i2b2.protocol.pm.GetUserConfigurationRequest
import scala.util.Try
import net.shrine.util.Loggable

/**
 * @author clint
 * @date Apr 5, 2013
 */
trait PmAuthorizerComponent { self: PmHttpClientComponent with Loggable =>

  import PmAuthorizerComponent._

  object Pm {
    def authorize(projectId: String, neededRoles: Set[String], authn: AuthenticationInfo): AuthorizationStatus = {
      val request = GetUserConfigurationRequest(authn.domain, authn.username, authn.credential.value)
      
      def parsePmResult(responseXml: String): Try[Either[ErrorResponse, User]] = {
        Try{
          Right(User.fromI2b2(responseXml))
        }.recoverWith {
          case e: Exception => { 
        	debug(s"Couldn't extract a User from '$responseXml'")
        	
        	Try(Left(ErrorResponse.fromI2b2(responseXml))) 
          }
        }.recover {
          case e: Exception => { 
            warn(s"Couldn't understand response from PM: '$responseXml'", e) 
            
            Left(ErrorResponse(s"Error authorizing ${ authn.domain }:${ authn.username }: ${ e.getMessage }")) 
          }
        } 
      }
      
      val responseAttempt = Try {
        debug(s"Authorizing with PM cell at $pmEndpoint")
        
        httpClient.post(request.toI2b2String, pmEndpoint)
      }
      
      val authStatusAttempt = responseAttempt.flatMap(parsePmResult).map {
        case Right(user) => {
          val managerUserOption = for {
            roles <- user.rolesByProject.get(projectId)
            if neededRoles.forall(roles.contains)
          } yield user
          
          managerUserOption.map(Authorized(_)).getOrElse {
            NotAuthorized(s"User ${ authn.domain }:${ authn.username } does not have all the needed roles: ${ neededRoles.map("'" + _ + "'").mkString(", ") } in the project '$projectId'")
          }
        }
        case Left(ErrorResponse(message)) => NotAuthorized(message)
      }
      
      authStatusAttempt.getOrElse(NotAuthorized(s"Error authorizing ${ authn.domain }:${ authn.username } with PM at $pmEndpoint"))
    }
  }
}

object PmAuthorizerComponent {
  sealed trait AuthorizationStatus

  case class Authorized(user: User) extends AuthorizationStatus

  case class NotAuthorized(reason: String) extends AuthorizationStatus {
    def toErrorResponse = ErrorResponse(reason)
  }
}