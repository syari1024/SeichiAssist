package com.github.unchama.seichiassist.subsystems.vote.subsystems.fairyspeech

import cats.effect.Sync
import com.github.unchama.seichiassist.meta.subsystem.Subsystem
import com.github.unchama.seichiassist.subsystems.vote.subsystems.fairy.domain.property.FairyMessage
import com.github.unchama.seichiassist.subsystems.vote.subsystems.fairyspeech.bukkit.BukkitFairySpeechGateway
import com.github.unchama.seichiassist.subsystems.vote.subsystems.fairyspeech.domain.FairySpeechGateway
import com.github.unchama.seichiassist.subsystems.vote.subsystems.fairyspeech.service.FairySpeechService
import org.bukkit.entity.Player

trait System[F[_], Player] extends Subsystem[F] {

  val api: FairySpeechAPI[F, Player]

}

object System {

  def wired[F[_]: Sync](): System[F, Player] = {
    val speechGateway: Player => FairySpeechGateway[F] = player =>
      new BukkitFairySpeechGateway[F](player)
    val speechService: Player => FairySpeechService[F] = player =>
      new FairySpeechService[F](speechGateway(player))

    new System[F, Player] {
      override val api: FairySpeechAPI[F, Player] = new FairySpeechAPI[F, Player] {

        override def speech(player: Player, messages: Seq[FairyMessage]): F[Unit] = {
          // todo: fairyPlaySoundをインフラ層から取得するようにする
          speechService(player).makeSpeech(messages, true)
        }

      }
    }
  }

}
