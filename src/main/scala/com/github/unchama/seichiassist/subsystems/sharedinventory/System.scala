package com.github.unchama.seichiassist.subsystems.sharedinventory

import cats.effect.ConcurrentEffect
import com.github.unchama.generic.ContextCoercion
import com.github.unchama.seichiassist.meta.subsystem.Subsystem
import com.github.unchama.seichiassist.subsystems.sharedinventory.bukkit.command.ShareInventoryCommand
import com.github.unchama.seichiassist.subsystems.sharedinventory.domain.SharedFlag
import com.github.unchama.seichiassist.subsystems.sharedinventory.domain.bukkit.InventoryContents
import com.github.unchama.seichiassist.subsystems.sharedinventory.infrastracture.JdbcSharedInventoryPersistence
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

import java.util.UUID

trait System[F[_]] extends Subsystem[F] {
  val api: SharedInventoryAPI[F, Player]
}

object System {

  import cats.implicits._

  def wired[F[_]: ConcurrentEffect]: System[F] = {
    val persistence = new JdbcSharedInventoryPersistence[F]

    new System[F] {
      override implicit val api: SharedInventoryAPI[F, Player] =
        new SharedInventoryAPI[F, Player] {
          override def save(targetUuid: UUID, inventoryContents: InventoryContents): F[Unit] =
            ContextCoercion(persistence.save(targetUuid, inventoryContents))

          override def clear(targetUuid: UUID): F[Unit] =
            ContextCoercion(persistence.clear(targetUuid))

          override def load(targetUuid: UUID): F[Option[InventoryContents]] =
            ContextCoercion(persistence.load(targetUuid))

          override def sharedFlag(player: Player): F[SharedFlag] =
            load(player.getUniqueId).map {
              case Some(_) => SharedFlag.Sharing
              case None    => SharedFlag.NotSharing
            }

        }
      override val commands: Map[String, TabExecutor] = {
        Map("shareinv" -> new ShareInventoryCommand[F].executor)
      }
    }
  }

}
