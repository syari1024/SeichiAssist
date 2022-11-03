package com.github.unchama.seichiassist.subsystems.seichilevelupgift.bukkit

import cats.data.Kleisli
import cats.effect.Sync
import com.github.unchama.generic.ContextCoercion
import com.github.unchama.minecraft.actions.OnMinecraftServerThread
import com.github.unchama.seichiassist.data.ItemData
import com.github.unchama.seichiassist.subsystems.gacha.GachaDrawAPI
import com.github.unchama.seichiassist.subsystems.gachaprize.GachaPrizeAPI
import com.github.unchama.seichiassist.subsystems.gachapoint.GachaPointApi
import com.github.unchama.seichiassist.subsystems.gachapoint.domain.gachapoint.GachaPoint
import com.github.unchama.seichiassist.subsystems.seichilevelupgift.domain.{
  Gift,
  GrantLevelUpGiftAlgebra
}
import com.github.unchama.seichiassist.util.InventoryOperations.grantItemStacksEffect
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class BukkitGrantLevelUpGift[F[_]: Sync: OnMinecraftServerThread, G[_]: ContextCoercion[*[
  _
], F]](
  implicit gachaPointApi: GachaPointApi[F, G, Player],
  gachaPrizeAPI: GachaPrizeAPI[F, ItemStack, Player],
  gachaDrawAPI: GachaDrawAPI[F, Player]
) extends GrantLevelUpGiftAlgebra[F, Player] {
  override def grantGiftItem(item: Gift.Item): Kleisli[F, Player, Unit] = {
    val itemStack = item match {
      case Gift.Item.SuperPickaxe => ItemData.getSuperPickaxe(1)
      case Gift.Item.GachaApple   => gachaPrizeAPI.staticGachaPrizeFactory.gachaRingo
      case Gift.Item.Elsa         => ItemData.getElsa(1)
    }

    grantItemStacksEffect[F](itemStack)
  }

  override def grantGachaPoint(gachaPoint: GachaPoint): Kleisli[F, Player, Unit] =
    gachaPointApi.addGachaPoint(gachaPoint).mapK[F](ContextCoercion.asFunctionK)

  override def runGachaForPlayer: Kleisli[F, Player, Unit] = Kleisli { player =>
    gachaDrawAPI.drawGacha(player, 1)
  }
}

object BukkitGrantLevelUpGift {

  implicit def apply[F[_]: Sync: OnMinecraftServerThread, G[_]: ContextCoercion[*[_], F]](
    implicit gachaPrizeAPI: GachaPrizeAPI[F, ItemStack, Player],
    gachaPointApi: GachaPointApi[F, G, Player],
    gachaDrawAPI: GachaDrawAPI[F, Player]
  ): GrantLevelUpGiftAlgebra[F, Player] =
    new BukkitGrantLevelUpGift[F, G]

}
