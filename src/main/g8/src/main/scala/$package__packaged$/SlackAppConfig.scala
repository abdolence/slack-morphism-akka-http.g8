package $package$

/**
 * Your slack App profile data
 */
case class SlackAppConfig(
    appId: String,
    clientId: String,
    clientSecret: String,
    signingSecret: String,
    redirectUrl: Option[String] = None,
    botScope: String =
      "commands,app_mentions:read,channels:history,channels:read,dnd:read,emoji:read,im:history,im:read,im:write,mpim:history,mpim:read,mpim:write,reactions:read,reactions:write,reminders:read,reminders:write,team:read,users.profile:read,users:read,groups:history,groups:read,chat:write"
)

object SlackAppConfig {
  final val empty = SlackAppConfig( null, null, null, null )
}
