package com.github.unchama.seichiassist.subsystems.vote

import com.github.unchama.seichiassist.subsystems.vote.domain._

import java.util.UUID

trait VoteWriteAPI[F[_]] {

  /**
   * 投票ポイントをインクリメントする作用
   */
  def voteCounterIncrement(playerName: PlayerName): F[Unit]

  /**
   * 連続投票を更新する作用
   */
  def updateChainVote(playerName: PlayerName): F[Unit]

  /**
   * effectPointを減少させる作用
   */
  def decreaseEffectPoint(uuid: UUID, effectPoint: EffectPoint): F[Unit]

  /**
   * effectPointを10増加させる作用
   */
  def increaseEffectPointsByTen(uuid: UUID): F[Unit]

  /**
   * 投票特典を受け取った回数を増加させる作用
   */
  def increaseVoteBenefits(uuid: UUID, benefit: VoteBenefit): F[Unit]

}

object VoteWriteAPI {

  def apply[F[_]](implicit voteWriteAPI: VoteWriteAPI[F]): VoteWriteAPI[F] = implicitly

}

trait VoteReadAPI[F[_], Player] {

  /**
   * 投票回数を返す作用
   */
  def voteCounter(uuid: UUID): F[VoteCounter]

  /**
   * 連続投票日数を返す作用
   */
  def chainVoteDayNumber(uuid: UUID): F[ChainVoteDayNumber]

  /**
   * effectPointを返す作用
   */
  def effectPoints(player: Player): F[EffectPoint]

  /**
   * 投票特典を受け取った回数を返す作用
   */
  def receivedVoteBenefits(uuid: UUID): F[VoteBenefit]

  /**
   * 投票特典を受け取っていない回数を返す作用
   */
  def notReceivedVoteBenefits(uuid: UUID): F[VoteBenefit]

}

object VoteReadAPI {

  def apply[F[_], Player](
    implicit voteReadAPI: VoteReadAPI[F, Player]
  ): VoteReadAPI[F, Player] = implicitly

}

trait VoteAPI[F[_], Player] extends VoteReadAPI[F, Player] with VoteWriteAPI[F]
