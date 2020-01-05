package $package$

import akka.Done
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import com.typesafe.scalalogging._

import scala.concurrent.Await
import scala.concurrent.duration._

object Main extends App with StrictLogging {

  val APP_NAME = "$name$"
  val APP_VER = "0.0.1"

  private def createActorSystem( start: ActorContext[Done] => Unit ) = {
    ActorSystem[Done](
      Behaviors.setup[Done] { ctx =>
        addShutdownHook { () =>
          logger.debug( "Terminating Akka system..." )
          ctx.system.terminate()
        }

        start( ctx )

        Behaviors.receiveMessage {
          case Done =>
            Behaviors.stopped
        }
      },
      "$package$"
    )
  }

  private def createParser() = {
    new scopt.OptionParser[AppConfig]( APP_NAME ) {
      head( APP_NAME, APP_VER )
      opt[String]( "host" ).abbr( "h" ).text( "HTTP Server Host" ).action { ( v, c ) =>
        c.copy( httpServerHost = v )
      }
      opt[Int]( "port" ).abbr( "p" ).text( "HTTP Server Port" ).action { ( v, c ) =>
        c.copy( httpServerPort = v )
      }
      opt[String]( "slack-app-id" ).abbr( "aid" ).text( "Slack App Id" ).required().action { ( v, c ) =>
        c.copy( slackAppConfig = c.slackAppConfig.copy( appId = v ) )
      }
      opt[String]( "slack-client-id" ).abbr( "cid" ).text( "Slack Client Id" ).required().action { ( v, c ) =>
        c.copy( slackAppConfig = c.slackAppConfig.copy( clientId = v ) )
      }
      opt[String]( "slack-client-secret" ).abbr( "cs" ).text( "Slack Client Secret" ).required().action { ( v, c ) =>
        c.copy( slackAppConfig = c.slackAppConfig.copy( clientSecret = v ) )
      }
      opt[String]( "slack-signing-secret" ).abbr( "ss" ).text( "Slack Signing Secret" ).required().action { ( v, c ) =>
        c.copy( slackAppConfig = c.slackAppConfig.copy( signingSecret = v ) )
      }
      opt[String]( "slack-redirect-url" ).abbr( "rurl" ).text( "Slack Redirect URL" ).action { ( v, c ) =>
        c.copy( slackAppConfig = c.slackAppConfig.copy( redirectUrl = Some( v ) ) )
      }
      opt[String]( "slack-install-bot-scope" )
        .abbr( "bot-scope" )
        .text( "Slack OAuth Install Scope for a bot token" )
        .action { ( v, c ) =>
          c.copy( slackAppConfig = c.slackAppConfig.copy( botScope = v ) )
        }
      opt[String]( "sway-db-path" ).abbr( "dbpath" ).text( "Path to data for SwayDb" ).action { ( v, c ) =>
        c.copy( databaseDir = v )
      }
    }
  }

  private def addShutdownHook( hook: () => Unit ) = {
    Runtime.getRuntime.addShutdownHook( new Thread() {
      override def run() = {
        try {
          hook()
        } catch {
          case ex: Throwable => {
            logger.warn( ex.toString, ex )
          }
        }
      }
    } )
  }

  val parser = createParser()
  parser.parse( args, AppConfig( slackAppConfig = SlackAppConfig.empty ) ) match {
    case Some( config: AppConfig ) => {
      logger.info( "Loading..." )

      val actorSystem = createActorSystem { ctx: ActorContext[Done] =>
        logger.debug( "Akka System is ready" )
        val httpServer = ctx.spawnAnonymous( AkkaHttpServer.run )
        httpServer ! AkkaHttpServer.Start( config )
      }
      Await.result( actorSystem.whenTerminated, Duration.Inf )
    }
    case _ => parser.showTryHelp()
  }

}
