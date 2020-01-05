package $package$.routes

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.stream.typed.scaladsl.ActorMaterializer
import com.typesafe.scalalogging._
import io.circe.parser._
import org.latestbit.slack.morphism.client.{ SlackApiClient, SlackApiToken }
import org.latestbit.slack.morphism.client.reqresp.views.SlackApiViewsOpenRequest
import org.latestbit.slack.morphism.events._

import $package$.AppConfig
import $package$.db.SlackTokensDb
import $package$.templates._

import scala.concurrent.ExecutionContext

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
        case interactionEvent: SlackInteractionEvent => {
          logger.warn( s"We don't handle this interaction in this example: \${interactionEvent}" )
          complete( StatusCodes.OK )
        }
      }
    }
  }

}
