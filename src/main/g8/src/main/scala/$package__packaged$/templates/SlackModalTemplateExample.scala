package $package$.templates

import org.latestbit.slack.morphism.client.templating.SlackModalViewTemplate
import org.latestbit.slack.morphism.messages._

class SlackModalTemplateExample() extends SlackModalViewTemplate {

  override def titleText(): SlackBlockPlainText = plain"Test Modal"

  override def closeText(): Option[SlackBlockPlainText] = Some( plain"Got it" )

  override def renderBlocks(): List[SlackBlock] =
    blocks(
      sectionBlock(
        text = md"Just a dummy window here, sorry"
      )
    )

}
