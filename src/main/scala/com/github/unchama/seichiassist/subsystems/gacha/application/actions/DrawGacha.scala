package com.github.unchama.seichiassist.subsystems.gacha.application.actions

import com.github.unchama.seichiassist.subsystems.gacha.GachaAPI

trait DrawGacha[F[_], Player] {

  def draw(player: Player, amount: Int)(implicit gachaAPI: GachaAPI[F]): F[Unit]

}

object DrawGacha {

  def apply[F[_], Player](implicit ev: DrawGacha[F, Player]): DrawGacha[F, Player] = ev

}
