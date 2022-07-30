package com.github.unchama.seichiassist.subsystems.vote.subsystems.fairy.bukkit.actions

import cats.Applicative
import cats.effect.ConcurrentEffect.ops.toAllConcurrentEffectOps
import cats.effect.{ConcurrentEffect, Sync}
import com.github.unchama.generic.ContextCoercion
import com.github.unchama.seichiassist.{MineStackObjectList, SeichiAssist}
import com.github.unchama.seichiassist.subsystems.breakcount.BreakCountAPI
import com.github.unchama.seichiassist.subsystems.mana.ManaApi
import com.github.unchama.seichiassist.subsystems.mana.domain.ManaAmount
import com.github.unchama.seichiassist.subsystems.vote.VoteAPI
import com.github.unchama.seichiassist.subsystems.vote.subsystems.fairy.FairyAPI
import com.github.unchama.seichiassist.subsystems.vote.subsystems.fairy.application.actions.RecoveryMana
import com.github.unchama.seichiassist.subsystems.vote.subsystems.fairy.bukkit.FairySpeech
import com.github.unchama.seichiassist.subsystems.vote.subsystems.fairy.domain.property.{
  AppleAmount,
  AppleOpenState,
  FairyManaRecoveryState,
  FairyUsingState
}
import com.github.unchama.targetedeffect.{SequentialEffect, UnfocusedEffect}
import com.github.unchama.targetedeffect.commandsender.MessageEffect
import org.bukkit.ChatColor._
import org.bukkit.entity.Player

import java.time.LocalDateTime
import scala.util.Random

object BukkitRecoveryMana {

  import cats.implicits._

  def apply[F[_]: ConcurrentEffect: Applicative, G[_]: ContextCoercion[*[_], F]](
    player: Player
  )(
    implicit breakCountAPI: BreakCountAPI[F, G, Player],
    fairyAPI: FairyAPI[F, G, Player],
    voteAPI: VoteAPI[F, Player],
    manaApi: ManaApi[F, G, Player]
  ): RecoveryMana[F] = new RecoveryMana[F] {
    override def recovery: F[Unit] = {
      for {
        fairyUsingState <- fairyAPI.fairyUsingState(player)
        fairyEndTimeOpt <- fairyAPI.fairyEndTime(player)
        endTime = fairyEndTimeOpt.get.endTimeOpt.get
        isUsing = fairyUsingState == FairyUsingState.Using
        _ <- {
          new FairySpeech[F, G]
            .bye(player) >> fairyAPI.updateFairyUsingState(player, FairyUsingState.NotUsing)
        }.whenA(
          // 終了時間が今よりも過去だったとき(つまり有効時間終了済み)
          isUsing && endTime.isBefore(LocalDateTime.now())
        )
        oldManaAmount <- ContextCoercion {
          manaApi.readManaAmount(player)
        }

        _ <- {
          new FairySpeech[F, G].speechRandomly(player, FairyManaRecoveryState.full)
        }.whenA(isUsing && oldManaAmount.isFull)

      } yield ()
      ContextCoercion(manaApi.readManaAmount(player)).map { oldManaAmount =>
        if (fairyAPI.fairyUsingState(player).toIO.unsafeRunSync() == FairyUsingState.Using) {
          if (
            fairyAPI
              .fairyEndTime(player)
              .toIO
              .unsafeRunSync()
              .get
              .endTimeOpt
              .get
              .isBefore(LocalDateTime.now())
          ) {
            // 終了時間が今よりも過去だったとき(つまり有効時間終了済み)
            (new FairySpeech[F, G].bye(player) >> fairyAPI.updateFairyUsingState(
              player,
              FairyUsingState.NotUsing
            )).toIO.unsafeRunSync()
          } else {
            // 有効期限内
            if (oldManaAmount.isFull) {
              new FairySpeech[F, G]
                .speechRandomly(player, FairyManaRecoveryState.full)
                .toIO
                .unsafeRunSync()
            } else {
              val uuid = player.getUniqueId

              val eff = for {
                seichiAmountData <- ContextCoercion(
                  breakCountAPI.seichiAmountDataRepository(player).read
                )
                defaultRecoveryManaAmount <- fairyAPI.fairyRecoveryMana(uuid)
                chainVoteNumber <- voteAPI.chainVoteDayNumber(uuid)
                appleOpenState <- fairyAPI.appleOpenState(uuid)
              } yield {
                val playerLevel = seichiAmountData.levelCorrespondingToExp

                val playerdata = SeichiAssist.playermap(player.getUniqueId)
                val gachaRingoObject =
                  MineStackObjectList.findByName("gachaimo").unsafeRunSync().get
                val mineStackedGachaRingoAmount =
                  playerdata.minestack.getStackedAmountOf(gachaRingoObject)

                val isAppleOpenStateIsOpenOROpenALittle =
                  appleOpenState == AppleOpenState.OpenALittle || appleOpenState == AppleOpenState.Open
                val isManaExistsSeventyFivePercent = oldManaAmount.ratioToCap.exists(_ >= 0.75)

                val defaultAmount = Math.pow(playerLevel.level / 10, 2)

                val chainVoteDayNumber = chainVoteNumber.value
                // 連続投票を適用した除算量
                val chainVoteDivisionAmount =
                  if (chainVoteDayNumber >= 30) 2
                  else if (chainVoteDayNumber >= 10) 1.2
                  else if (chainVoteDayNumber >= 3) 1.25
                  else 1

                // りんごの開放状況を適用した除算量
                val appleOpenStateDivisionAmount =
                  if (isAppleOpenStateIsOpenOROpenALittle && isManaExistsSeventyFivePercent) 2
                  else 1

                // りんごの開放状況まで適用したりんごの消費量 (暫定)
                val appleOpenStateReflectedAmount =
                  (defaultAmount / chainVoteDivisionAmount).toInt / appleOpenStateDivisionAmount

                // 妖精がつまみ食いする量
                val amountEatenByKnob =
                  if (appleOpenStateReflectedAmount >= 10)
                    new Random().nextInt(appleOpenStateReflectedAmount / 10)
                  else 0

                /*
                     最終的に算出されたりんごの消費量
                      (現時点では持っているりんごの数を
                      考慮していないので消費量は確定していない)
                 */
                val finallyAppleConsumptionAmount =
                  Math.max(appleOpenStateReflectedAmount + amountEatenByKnob, 1)

                // りんごの消費量
                val appleConsumptionAmount =
                  if (appleOpenState == AppleOpenState.NotOpen) {
                    0
                  } else {
                    if (mineStackedGachaRingoAmount > finallyAppleConsumptionAmount)
                      finallyAppleConsumptionAmount
                    else mineStackedGachaRingoAmount
                  }

                // マナの回復量を算出する
                val recoveryManaAmount = {
                  val appleOpenStateDivision = {
                    if (isAppleOpenStateIsOpenOROpenALittle && isManaExistsSeventyFivePercent) 2
                    else if (appleOpenState == AppleOpenState.NotOpen) 4
                    else 1
                  }

                  val reflectedAppleOpenStateAmount =
                    defaultRecoveryManaAmount.recoveryMana / appleOpenStateDivision

                  // minestackに入っているりんごの数を適用したマナの回復量
                  val reflectedMineStackedAmount =
                    if (finallyAppleConsumptionAmount > mineStackedGachaRingoAmount) {
                      if (mineStackedGachaRingoAmount == 0) {
                        reflectedAppleOpenStateAmount / {
                          if (appleOpenState == AppleOpenState.Open) 4
                          else if (appleOpenState == AppleOpenState.OpenALittle) 4
                          else 2
                        }
                      } else {
                        if (
                          (mineStackedGachaRingoAmount / finallyAppleConsumptionAmount) <= 0.5
                        )
                          (reflectedAppleOpenStateAmount * 0.5).toInt
                        else
                          (reflectedAppleOpenStateAmount * mineStackedGachaRingoAmount / finallyAppleConsumptionAmount).toInt
                      }
                    } else reflectedAppleOpenStateAmount

                  (reflectedMineStackedAmount - reflectedMineStackedAmount / 100) +
                    (if (reflectedMineStackedAmount >= 50)
                       Random.nextInt(reflectedMineStackedAmount / 50)
                     else 0)
                }

                // minestackからりんごを消費する
                playerdata
                  .minestack
                  .subtractStackedAmountOf(gachaRingoObject, appleConsumptionAmount)

                // 消費したりんごの量を保存する
                fairyAPI
                  .increaseAppleAteByFairy(uuid, AppleAmount(appleConsumptionAmount.toInt))
                  .toIO
                  .unsafeRunSync()

                // マナを回復する
                ContextCoercion(
                  manaApi.manaAmount(player).restoreAbsolute(ManaAmount(recoveryManaAmount))
                ).toIO.unsafeRunSync()

                if (finallyAppleConsumptionAmount > mineStackedGachaRingoAmount) {
                  MessageEffect(s"$RESET$YELLOW${BOLD}MineStackにがちゃりんごがないようです。。。")
                    .apply(player)
                    .unsafeRunSync()
                }

                SequentialEffect(
                  UnfocusedEffect {
                    new FairySpeech[F, G]
                      .speechRandomly(
                        player,
                        if (finallyAppleConsumptionAmount > mineStackedGachaRingoAmount)
                          FairyManaRecoveryState.notConsumptionApple
                        else FairyManaRecoveryState.consumptionApple
                      )
                      .toIO
                      .unsafeRunSync()
                  },
                  MessageEffect(s"$RESET$YELLOW${BOLD}マナ妖精が${recoveryManaAmount}マナを回復してくれました"),
                  if (appleConsumptionAmount != 0)
                    MessageEffect(
                      s"$RESET$YELLOW${BOLD}あっ！${appleConsumptionAmount}個のがちゃりんごが食べられてる！"
                    )
                  else MessageEffect(s"$RESET$YELLOW${BOLD}あなたは妖精にりんごを渡しませんでした。")
                ).apply(player).unsafeRunSync()
              }
              eff.toIO.unsafeRunSync()
            }
          }
        } else Sync[F].unit
      }
    }

  }

}
