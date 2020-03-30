package $package$.db

import akka.actor.typed._
import akka.actor.typed.scaladsl._

import cats.implicits._
import com.typesafe.scalalogging._
import akka.actor.typed.scaladsl.adapter._

import swaydb.{ Set => _, _ }
import swaydb.serializers.Default._
import swaydb.IO.ApiIO
import swaydb.data.slice.Slice

import scala.concurrent.ExecutionContextExecutor

import $package$.config._

object SlackTokensDb extends StrictLogging {

  case class TokenRecord( tokenType: String, tokenValue: String, userId: String, scope: String )
  case class TeamTokensRecord( teamId: String, tokens: List[TokenRecord] )

  implicit object TeamTokensRecordSwayDbSerializer extends swaydb.serializers.Serializer[TeamTokensRecord] {
    import io.circe.parser._
    import io.circe.syntax._
    import io.circe.generic.auto._

    override def write( data: TeamTokensRecord ): Slice[Byte] = {
      Slice( data.asJson.dropNullValues.noSpaces.getBytes )
    }

    override def read( data: Slice[Byte] ): TeamTokensRecord = {
      decode[TeamTokensRecord]( new String( data.toArray ) ).valueOr( throw _ )
    }
  }

  sealed trait Command
  case class OpenDb( config: AppConfig ) extends Command
  case class Close() extends Command
  case class InsertToken( teamId: String, tokenRecord: TokenRecord ) extends Command
  case class RemoveTokens( teamId: String, users: Set[String] ) extends Command
  case class ReadTokens( teamId: String, ref: akka.actor.typed.ActorRef[Option[TeamTokensRecord]] ) extends Command

  type FunctionType = PureFunction[String, TeamTokensRecord, Apply.Map[TeamTokensRecord]]
  type SwayDbType = Map[String, TeamTokensRecord, FunctionType, ApiIO]

  val run: Behavior[Command] = runBehavior( None )

  private def runBehavior( swayMap: Option[SwayDbType] ): Behavior[Command] = {
    Behaviors.setup { implicit context =>
      implicit val system = context.system
      implicit val classicSystem = context.system.toClassic
      implicit val ec: ExecutionContextExecutor = context.system.executionContext

      Behaviors.receiveMessage {
        case OpenDb( config ) => {
          logger.info( s"Opening sway db in \${config.databaseDir}" )
          val map: SwayDbType =
            persistent.Map[String, TeamTokensRecord, FunctionType, IO.ApiIO]( dir = config.databaseDir ).get

          runBehavior( Some( map ) )
        }

        case InsertToken( teamId: String, tokenRecord ) => {
          swayMap.foreach { swayMap =>
            swayMap
              .get( key = teamId )
              .map(
                _.map(rec =>
                  rec.copy(tokens =
                    rec.tokens.filterNot( _.userId == tokenRecord.userId ) :+ tokenRecord
                  )
                ).getOrElse(
                  TeamTokensRecord(
                    teamId = teamId,
                    tokens = List(
                      tokenRecord
                    )
                  )
                )
              )
              .foreach { record =>
                logger.debug( s"Inserting record for : \${teamId}/\${tokenRecord.userId}" )
                swayMap.put( teamId, record )
              }
          }
          Behavior.same
        }

        case ReadTokens( teamId, ref ) => {
          swayMap.foreach { swayMap =>
            swayMap.get( key = teamId ).foreach { record =>
              ref ! record
            }
          }
          Behavior.same
        }

        case RemoveTokens( teamId: String, users: Set[String] ) => {
          swayMap.foreach { swayMap =>
            swayMap
              .get( key = teamId )
              .foreach( _.foreach { record =>
                swayMap.put(
                  teamId,
                  record.copy(
                    tokens = record.tokens.filterNot( token => users.contains( token.userId ) )
                  )
                )
              })
          }
          Behavior.same
        }

        case Close() => {
          Behavior.stopped
        }
      }
    }

  }
}
