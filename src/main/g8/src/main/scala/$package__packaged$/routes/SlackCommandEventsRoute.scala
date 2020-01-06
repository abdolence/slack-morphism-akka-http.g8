package $package$.routes

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.stream.typed.scaladsl.ActorMaterializer
import com.typesafe.scalalogging._
import io.circe.parser._
import org.latestbit.slack.morphism.client.reqresp.chat.SlackApiPostEventReply
import org.latestbit.slack.morphism.client.reqresp.views.SlackApiViewsOpenRequest
import org.latestbit.slack.morphism.client._
import org.latestbit.slack.morphism.common.SlackResponseTypes
import org.latestbit.slack.morphism.events._

import $package$.AppConfig
import $package$.db.SlackTokensDb
import $package$.templates._

import scala.concurrent.ExecutionContext

class SlackCommandEventsRoute(
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
    path( "command" ) {
      post {
        extractSlackSignedRequest { requestBody =>
          logger.debug( s"Received a command: \${requestBody}" )
          formFields(
            "team_id",
            "team_domain".?,
            "channel_id",
            "channel_name".?,
            "user_id",
            "user_name".?,
            "command",
            "text".?,
            "response_url",
            "trigger_id"
          ) {
            case (
                team_id,
                team_domain,
                channel_id,
                channel_name,
                user_id,
                user_name,
                command,
                text,
                response_url,
                trigger_id
                ) =>
              routeWithSlackApiToken( team_id ) { implicit slackApiToken =>
                // Sending additional reply using response_url
                val commandReply = new SlackSampleMessageReplyTemplateExample(
                  text.getOrElse( "" )
                )

                slackApiClient.chat
                  .postEventReply(
                    response_url = response_url,
                    SlackApiPostEventReply(
                      commandReply.renderPlainText(),
                      blocks = commandReply.renderBlocks(),
                      response_type = Some( SlackResponseTypes.EPHEMERAL )
                    )
                  )
                  .foreach {
                    case Right( resp ) => {
                      logger.info( s"Sent a reply message: \${resp}" )

                    }
                    case Left( err ) => {
                      logger.error( s"Unable to sent a reply message: ", err )
                    }
                  }

                // Sending work in progress message
                completeWithJson(
                  SlackApiPostEventReply(
                    text = "Working on it..."
                  )
                )
              }
          }
        }
      }
    }
  }

}
