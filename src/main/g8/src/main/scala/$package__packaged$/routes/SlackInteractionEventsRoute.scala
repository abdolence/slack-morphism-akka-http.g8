package $package$.routes

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.stream.typed.scaladsl.ActorMaterializer
import com.typesafe.scalalogging._
import io.circe.parser._

import cats.implicits._
import scala.concurrent.ExecutionContext

import org.latestbit.slack.morphism.client.{ SlackApiClient, SlackApiToken }
import org.latestbit.slack.morphism.client.reqresp.views.SlackApiViewsOpenRequest
import org.latestbit.slack.morphism.events._

import $package$.AppConfig
import $package$.db.SlackTokensDb
import $package$.templates._

class SlackInteractionEventsRoute(
    implicit ctx: ActorContext[_],
    materializer: ActorMaterializer,
    config: AppConfig,
    slackApiClient: SlackApiClient,
    slackTokensDb: ActorRef[SlackTokensDb.Command]
) extends StrictLogging
    with AkkaHttpServerRoutesSupport
    with Directives {

  implicit val ec: ExecutionContext = ctx.system.executionContext

  val routes: Route = {
    path( "interaction" ) {
      post {
        extractSlackSignedRequest { requestBody =>
          formField( "payload" ) { payload =>
            decode[SlackInteractionEvent]( payload ) match {
              case Right( event ) => onEvent( event )
              case Left( ex ) => {
                logger.error( s"Can't decode push event from Slack: \${ex.toString}\n\${requestBody}" )
                complete( StatusCodes.BadRequest )
              }
            }
          }
        }
      }
    }
  }

  private def showDummyModal( triggerId: String )( implicit slackApiToken: SlackApiToken ) = {
    val modalTemplateExample = new SlackModalTemplateExample()
    onSuccess(
      slackApiClient.views.open(
        SlackApiViewsOpenRequest(
          trigger_id = triggerId,
          view = modalTemplateExample.toModalView()
        )
      )
    ) {
      case Right( resp ) => {
        logger.info( s"Modal view has been opened: \${resp}" )
        complete( StatusCodes.OK )
      }
      case Left( err ) => {
        logger.error( s"Unable to open modal view", err )
        complete( StatusCodes.InternalServerError )
      }
    }
  }

  def removeTokens( workspaceId: String, re: SlackTokensRevokedEvent ) = {
      slackTokensDb ! SlackTokensDb.RemoveTokens( workspaceId, re.tokens.oauth.toSet ++ re.tokens.bot.toSet )
      complete( StatusCodes.OK )
  }

  def onEvent( event: SlackInteractionEvent ): Route = {
    routeWithSlackApiToken( event.team.id ) { implicit slackApiToken =>
      event match {
        case blockActionEvent: SlackInteractionBlockActionEvent => {
          logger.warn( s"Received a block action event: \${blockActionEvent}" )
          showDummyModal( blockActionEvent.trigger_id )
        }
        case messageActionEvent: SlackInteractionMessageActionEvent => {
          logger.warn( s"Received a message action event: \${messageActionEvent}" )
          showDummyModal( messageActionEvent.trigger_id )
        }
        case actionSubmissionEvent: SlackInteractionViewSubmissionEvent => {
            actionSubmissionEvent.view.stateParams.state.foreach { state =>
                logger.info( s"Received action submission state: \${state}" )
            }

            // For some reason, Slack requires at least an empty HTTP body to respond to view submissions to avoid timeout
            // Just StatusCodes.OK isn't enough here
            complete(
                StatusCodes.OK,
                HttpEntity(
                    ContentTypes.`text/plain(UTF-8)`,
                    ""
                )
            )
        }
        case removeToken: SlackTokensRevokedEvent => {
            removeTokens( event.team.id, removeToken )
        }
        case interactionEvent: SlackInteractionEvent => {
          logger.warn( s"We don't handle this interaction in this example: \${interactionEvent}" )
          complete( StatusCodes.OK )
        }
      }
    }
  }

}
