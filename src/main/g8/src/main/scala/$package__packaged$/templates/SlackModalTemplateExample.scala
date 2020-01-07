package $package$.templates

import org.latestbit.slack.morphism.client.templating.SlackModalViewTemplate
import org.latestbit.slack.morphism.messages._

class SlackModalTemplateExample() extends SlackModalViewTemplate {

  override def titleText(): SlackBlockPlainText = plain"Test Modal"

  override def closeText(): Option[SlackBlockPlainText] = Some( plain"Got it" )

  override def renderBlocks(): List[SlackBlock] =
      blocks(
          sectionBlock(
              text = md"Just a dummy window here, sorry",
              accessory = multiStaticSelect(
                  placeholder = plain"With a dummy menu",
                  action_id = "-",
                  options = choiceItems(
                      choiceItem( text = plain"First Option", value = "1" ),
                      choiceItem( text = plain"Second Option", value = "2" ),
                      choiceItem( text = plain"Third Option", value = "3" )
                  )
              )
          ),
          inputBlock(
              label = plain"Dummy radio",
              element = radioButtons(
                  action_id = "-",
                  options = choiceItems(
                      choiceItem( text = plain"Radio 1", value = "1" ),
                      choiceItem( text = plain"Radio 2", value = "2" ),
                      choiceItem( text = plain"Radio 3", value = "3" )
                  )
              )
          )
      )

}
