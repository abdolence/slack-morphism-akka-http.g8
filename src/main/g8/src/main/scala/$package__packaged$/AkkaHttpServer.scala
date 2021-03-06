package $package$

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.typed.scaladsl._
import com.typesafe.scalalogging._
import sttp.client.akkahttp.AkkaHttpBackend

import org.latestbit.slack.morphism.client._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util._

import cats.implicits._

import $package$.db.SlackTokensDb
import $package$.routes._
import $package$.config._

object AkkaHttpServer extends StrictLogging {
  sealed trait Command
  case class Start( config: AppConfig ) extends Command
  case class Stop() extends Command

  val run: Behavior[Command] = runBehavior( None )

  private case class HttpServerState(
      httpBinding: Future[Http.ServerBinding],
      tokensDbRef: ActorRef[SlackTokensDb.Command]
  )

  private def runBehavior( serverState: Option[HttpServerState] ): Behavior[Command] =
    Behaviors.setup { implicit context =>
      implicit val system = context.system
      implicit val classicSystem = context.system.toClassic
      implicit val materializer = ActorMaterializer()
      implicit val ec: ExecutionContextExecutor = context.system.executionContext

      Behaviors.receiveMessage {
        case Start( config ) => {
          logger.info(
            s"Starting routes on \${config.httpServerHost}:\${config.httpServerPort}"
          )
          implicit val appConfig = config
          implicit val akkaSttpBackend = AkkaHttpBackend.usingActorSystem(classicSystem)
          implicit val slackApiClient = SlackApiClient.create[Future]()

          implicit val tokensDbRef = context.spawnAnonymous( SlackTokensDb.run )

          tokensDbRef ! SlackTokensDb.OpenDb( config )

          val slackPushEventsRoute = new SlackPushEventsRoute()
          val slackOAuthRoute = new SlackOAuthRoutes()
          val slackInteractionEventsRoute = new SlackInteractionEventsRoute()
          val slackCommandEventsRoute = new SlackCommandEventsRoute()

          val allRoutes: Route = {
            ignoreTrailingSlash {
              slackPushEventsRoute.routes ~
                  slackOAuthRoute.routes ~
                  slackInteractionEventsRoute.routes ~
                  slackCommandEventsRoute.routes
            }
          }

          val binding = Http().bindAndHandle(
            allRoutes,
            config.httpServerHost,
            config.httpServerPort
          )

          binding onComplete {
            case Success( bound ) =>
              logger.info(
                s"Server online at http://\${bound.localAddress.getHostString}:\${bound.localAddress.getPort}/"
              )
            case Failure( ex ) =>
              logger.error( s"Server could not start: ", ex )
              context.system.terminate()
          }

          runBehavior(
            Some(
              HttpServerState(
                httpBinding = binding,
                tokensDbRef = tokensDbRef
              )
            )
          )
        }

        case Stop() => {
          serverState.foreach { state =>
            state.httpBinding.foreach { binding =>
              logger.info("Stopping http server")
              binding.terminate( 5.seconds )
            }
            state.tokensDbRef ! SlackTokensDb.Close()
          }
          Behavior.stopped
        }
      }
    }

}
