package com.github.unchama.seichiassist.subsystems.minestack

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, Sync, SyncEffect}
import com.github.unchama.datarepository.bukkit.player.{BukkitRepositoryControls, PlayerDataRepository}
import com.github.unchama.datarepository.template.RepositoryDefinition
import com.github.unchama.generic.{ContextCoercion, ListExtra}
import com.github.unchama.minecraft.bukkit.objects.{BukkitItemStack, BukkitMaterial}
import com.github.unchama.minecraft.objects.{MinecraftItemStack, MinecraftMaterial}
import com.github.unchama.seichiassist.meta.subsystem.Subsystem
import com.github.unchama.seichiassist.subsystems.gachaprize.GachaPrizeAPI
import com.github.unchama.seichiassist.subsystems.minestack.application.repository.{MineStackObjectRepositoryDefinition, MineStackSettingsRepositoryDefinition, MineStackUsageHistoryRepositoryDefinitions}
import com.github.unchama.seichiassist.subsystems.minestack.bukkit.{BukkitMineStackObjectList, PlayerPickupItemListener}
import com.github.unchama.seichiassist.subsystems.minestack.domain.minestackobject.{MineStackObject, MineStackObjectList, MineStackObjectWithAmount}
import com.github.unchama.seichiassist.subsystems.minestack.domain._
import com.github.unchama.seichiassist.subsystems.minestack.domain.persistence.{MineStackGachaObjectPersistence, PlayerSettingPersistence}
import com.github.unchama.seichiassist.subsystems.minestack.infrastructure.{JdbcMineStackGachaObjectPersistence, JdbcMineStackObjectPersistence, JdbcPlayerSettingPersistence}
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack

trait System[F[_], Player, ItemStack] extends Subsystem[F] {

  val api: MineStackAPI[F, Player, ItemStack]

}

object System {

  import cats.implicits._

  def wired[F[_]: ConcurrentEffect, G[_]: SyncEffect: ContextCoercion[*[_], F]](
    implicit gachaPrizeAPI: GachaPrizeAPI[F, ItemStack, Player]
  ): F[System[F, Player, ItemStack]] = {
    implicit val minecraftMaterial: MinecraftMaterial[Material, ItemStack] = new BukkitMaterial
    implicit val minecraftItemStack: MinecraftItemStack[ItemStack] = new BukkitItemStack
    implicit val mineStackGachaObjectPersistence
      : MineStackGachaObjectPersistence[F, ItemStack] =
      new JdbcMineStackGachaObjectPersistence[F, ItemStack, Player]
    implicit val _mineStackObjectList: MineStackObjectList[F, ItemStack, Player] =
      new BukkitMineStackObjectList[F]
    implicit val playerSettingPersistence: Player => PlayerSettingPersistence[G] =
      player => new JdbcPlayerSettingPersistence[G](player.getUniqueId)

    for {
      allMineStackObjects <- _mineStackObjectList.allMineStackObjects
      mineStackObjectPersistence = new JdbcMineStackObjectPersistence[G, ItemStack, Player](
        allMineStackObjects
      )
      mineStackObjectRepositoryControls <- ContextCoercion(
        BukkitRepositoryControls.createHandles(
          MineStackObjectRepositoryDefinition
            .withContext[G, Player, ItemStack](mineStackObjectPersistence)
        )
      )

      mineStackUsageHistoryRepositoryControls <- ContextCoercion(
        BukkitRepositoryControls.createHandles(
          RepositoryDefinition
            .Phased
            .TwoPhased(
              MineStackUsageHistoryRepositoryDefinitions.initialization[G, Player, ItemStack],
              MineStackUsageHistoryRepositoryDefinitions.finalization[G, Player, ItemStack]
            )
        )
      )

      mineStackSettingsRepositoryControls <- ContextCoercion(
        BukkitRepositoryControls.createHandles(
          RepositoryDefinition
            .Phased
            .TwoPhased(
              MineStackSettingsRepositoryDefinition.initialization[G, Player],
              MineStackSettingsRepositoryDefinition.finalization[G, Player]
            )
        )
      )
    } yield {
      implicit val mineStackObjectRepository
        : PlayerDataRepository[Ref[F, List[MineStackObjectWithAmount[ItemStack]]]] =
        mineStackObjectRepositoryControls.repository.map(_.mapK(ContextCoercion.asFunctionK))
      val mineStackUsageHistoryRepository = mineStackUsageHistoryRepositoryControls.repository
      implicit val mineStackSettingRepository
        : PlayerDataRepository[MineStackSettings[G, Player]] =
        mineStackSettingsRepositoryControls.repository
      implicit val _tryIntoMineStack: TryIntoMineStack[F, Player, ItemStack] =
        new TryIntoMineStack[F, Player, ItemStack]

      new System[F, Player, ItemStack] {
        override val api: MineStackAPI[F, Player, ItemStack] =
          new MineStackAPI[F, Player, ItemStack] {
            override def subtractStackedAmountOf(
              player: Player,
              mineStackObject: MineStackObject[ItemStack],
              amount: Long
            ): F[Long] = {
              for {
                oldMineStackObjects <- mineStackObjectRepository(player).get
                updatedMineStackObjects <- mineStackObjectRepository(player).updateAndGet {
                  mineStackObjects =>
                    ListExtra.rePrepend(mineStackObjects)(
                      _.mineStackObject == mineStackObject,
                      _.decrease(amount)
                    )
                }
              } yield {
                ListExtra.computeDoubleList(oldMineStackObjects, updatedMineStackObjects)(
                  _.mineStackObject == mineStackObject,
                  {
                    case Some((oldMineStackObject, updatedMineStackObject)) =>
                      Math.abs(oldMineStackObject.amount - updatedMineStackObject.amount)
                    case None => 0
                  }
                )
              }
            }

            override def addStackedAmountOf(
              player: Player,
              mineStackObject: MineStackObject[ItemStack],
              amount: Int
            ): F[Unit] =
              mineStackObjectRepository(player).update { mineStackObjects =>
                ListExtra.rePrepend(mineStackObjects)(
                  _.mineStackObject == mineStackObject,
                  _.increase(amount)
                )
              }

            override def getStackedAmountOf(
              player: Player,
              mineStackObject: MineStackObject[ItemStack]
            ): F[Long] = for {
              mineStackObjects <- mineStackObjectRepository(player).get
            } yield {
              mineStackObjects
                .find(_.mineStackObject == mineStackObject)
                .map(_.amount)
                .getOrElse(0L)
            }

            override def getUsageHistory(player: Player): Vector[MineStackObject[ItemStack]] =
              mineStackUsageHistoryRepository(player).usageHistory

            override def addUsageHistory(
              player: Player,
              mineStackObject: MineStackObject[ItemStack]
            ): F[Unit] = Sync[F].delay {
              mineStackUsageHistoryRepository(player).addHistory(mineStackObject)
            }

            override def toggleAutoMineStack(player: Player): F[Unit] = for {
              currentState <- autoMineStack(player)
              _ <- ContextCoercion {
                if (currentState)
                  mineStackSettingRepository(player).toggleAutoMineStackTurnOff
                else mineStackSettingRepository(player).toggleAutoMineStackTurnOn
              }
            } yield ()

            override def autoMineStack(player: Player): F[Boolean] = for {
              currentState <- ContextCoercion(mineStackSettingRepository(player).currentState)
            } yield currentState

            override def tryIntoMineStack: TryIntoMineStack[F, Player, ItemStack] =
              _tryIntoMineStack

            override def mineStackObjectList: MineStackObjectList[F, ItemStack, Player] =
              _mineStackObjectList

            override def getAllMineStackGachaObjects
              : F[Vector[MineStackGachaObject[ItemStack]]] =
              mineStackGachaObjectPersistence.getAllMineStackGachaObjects

          }

        override val listeners: Seq[Listener] = Seq(new PlayerPickupItemListener[F, G]())

        override val managedRepositoryControls: Seq[BukkitRepositoryControls[F, _]] = Seq(
          mineStackObjectRepositoryControls,
          mineStackUsageHistoryRepositoryControls,
          mineStackSettingsRepositoryControls
        ).map(_.coerceFinalizationContextTo[F])
      }

    }
  }
}
