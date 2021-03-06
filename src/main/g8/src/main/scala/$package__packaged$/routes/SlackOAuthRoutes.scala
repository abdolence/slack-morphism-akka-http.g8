package $package$.routes

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.stream.typed.scaladsl.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging

import cats.implicits._
import scala.concurrent.{ ExecutionContext, Future }

import org.latestbit.slack.morphism.client._

import $package$.config._
import $package$.db.SlackTokensDb

class SlackOAuthRoutes(
    implicit ctx: ActorContext[_],
    materializer: ActorMaterializer,
    config: AppConfig,
    slackApiClient: SlackApiClientT[Future],
    slackTokensDb: ActorRef[SlackTokensDb.Command]
) extends StrictLogging
    with AkkaHttpServerRoutesSupport
    with Directives {

  implicit val ec: ExecutionContext = ctx.system.executionContext
  private val SLACK_AUTH_URL_V2 = "https://slack.com/oauth/v2/authorize"

  val routes: Route = {
    pathPrefix( "auth" ) {
      get {
        path( "install" ) {

          // Request a bot token scope with OAuth v2
          val baseParams = List[( String, Option[String] )](
            "client_id" -> Option( config.slackAppConfig.clientId ),
            "scope" -> Option( config.slackAppConfig.botScope ),
            "redirect_uri" -> config.slackAppConfig.redirectUrl
          ).flatMap { case ( k, v ) => v.map( k -> _ ) }

          val redirectUri = Uri( SLACK_AUTH_URL_V2 ).withQuery(
            Query(
              baseParams: _*
            )
          )

          logger.debug( s"Redirecting to: \${redirectUri.toString()}" )

          redirect(
            redirectUri,
            StatusCodes.Found
          )
        }
      } ~
        get {
          path( "callback" ) {
            parameters( "code".?, "error".?, "state".? ) {
              case ( code, error, state ) =>
                ( code, error ) match {
                  case ( Some( oauthCode ), _ ) => {
                    logger.info( s"Received OAuth access code: \${oauthCode}" )
                    onSuccess(
                      slackApiClient.oauth.v2.access(
                        clientId = config.slackAppConfig.clientId,
                        clientSecret = config.slackAppConfig.clientSecret,
                        code = oauthCode,
                        redirectUri = config.slackAppConfig.redirectUrl
                      )
                    ) {
                      case Right( tokens ) => {
                        logger.info( s"Received OAuth access tokens: \${tokens}" )

                        // In this example we store only a bot token, but is is common to store a user token as well

                        slackTokensDb ! SlackTokensDb.InsertToken(
                          teamId = tokens.team.id,
                          SlackTokensDb.TokenRecord(
                            tokenType = tokens.token_type,
                            scope = tokens.scope,
                            tokenValue = tokens.access_token,
                            userId = tokens.bot_user_id.getOrElse( tokens.authed_user.id )
                          )
                        )

                        complete( StatusCodes.OK )
                      }
                      case Left( err ) => {
                        logger.info( s"OAuth access error : \${err}" )
                        complete( StatusCodes.InternalServerError )
                      }
                    }
                  }
                  case ( _, Some( error ) ) => {
                    logger.info( s"OAuth error code: \${error}" )
                    complete( StatusCodes.OK )
                  }
                  case _ => {
                    logger.error( s"No OAuth code or error provided?" )
                    complete( StatusCodes.InternalServerError )
                  }
                }
            }
          }
        }
    }
  }

}
