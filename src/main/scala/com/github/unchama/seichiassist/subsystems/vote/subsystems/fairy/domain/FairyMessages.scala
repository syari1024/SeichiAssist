package com.github.unchama.seichiassist.subsystems.vote.subsystems.fairy.domain

case class FairyMessages(messages: String*) {
  require(messages.nonEmpty)
}
