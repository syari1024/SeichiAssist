package com.github.unchama.seichiassist.subsystems.gacha.bukkit.actions

import cats.effect.{IO, Sync}
import com.github.unchama.minecraft.actions.OnMinecraftServerThread
import com.github.unchama.seichiassist.concurrent.PluginExecutionContexts.onMainThread
import com.github.unchama.seichiassist.subsystems.gacha.GachaAPI
import com.github.unchama.seichiassist.subsystems.gacha.application.actions.DrawGacha
import com.github.unchama.seichiassist.subsystems.gacha.domain.GachaRarity.GachaRarity
import com.github.unchama.seichiassist.subsystems.gacha.domain.GachaRarity.GachaRarity.{
  Big,
  GachaRingoOrExpBottle,
  Gigantic,
  Regular
}
import com.github.unchama.seichiassist.subsystems.gacha.domain.{
  CanBeSignedAsGachaPrize,
  GrantState
}
import com.github.unchama.seichiassist.util.SendMessageEffect.sendMessageToEveryone
import com.github.unchama.seichiassist.util._
import net.md_5.bungee.api.chat.{HoverEvent, TextComponent}
import org.bukkit.ChatColor._
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class BukkitDrawGacha[F[_]: Sync: OnMinecraftServerThread](
  implicit gachaAPI: GachaAPI[F, ItemStack],
  canBeSignedAsGachaPrize: CanBeSignedAsGachaPrize[ItemStack]
) extends DrawGacha[F, Player] {

  import cats.implicits._
  import PlayerSendable._

  import scala.jdk.CollectionConverters._

  override def draw(player: Player, count: Int): F[Unit] = {
    for {
      gachaPrizes <- gachaAPI.runLottery(count)
      states <- gachaPrizes.traverse(gachaPrize =>
        new BukkitGrantGachaPrize().grantGachaPrize(gachaPrize)(player)
      )
    } yield {
      (gachaPrizes zip states).foreach {
        case (gachaPrize, state) =>
          val givenItem = gachaPrize.itemStack

          val additionalMessage = state match {
            case GrantState.GrantedMineStack =>
              s"${AQUA}景品をマインスタックに収納しました。"
            case GrantState.Dropped =>
              s"${AQUA}景品がドロップしました。"
            case _ =>
              ""
          }

          GachaRarity.of[ItemStack](gachaPrize) match {
            case Gigantic =>
              val loreWithoutOwnerName =
                givenItem.getItemMeta.getLore.asScala.toList.filterNot {
                  _ == s"$RESET${DARK_GREEN}所有者：${player.getName}"
                }

              val localizedEnchantmentList =
                givenItem.getItemMeta.getEnchants.asScala.toSeq.map {
                  case (enchantment, level) =>
                    s"$GRAY${EnchantNameToJapanese.getEnchantName(enchantment.getName, level)}"
                }

              import scala.util.chaining._
              val message =
                new TextComponent().tap { c =>
                  import c._
                  setText(
                    s"$AQUA${givenItem.getItemMeta.getDisplayName}${GOLD}を引きました！おめでとうございます！"
                  )
                  setHoverEvent {
                    new HoverEvent(
                      HoverEvent.Action.SHOW_TEXT,
                      Array(
                        new TextComponent(
                          s" ${givenItem.getItemMeta.getDisplayName}\n" +
                            ListFormatters.getDescFormat(localizedEnchantmentList.toList) +
                            ListFormatters.getDescFormat(loreWithoutOwnerName)
                        )
                      )
                    )
                  }
                }
              player.sendMessage(s"${RED}おめでとう！！！！！Gigantic☆大当たり！$additionalMessage")
              player.spigot().sendMessage(message)
              sendMessageToEveryone(s"$GOLD${player.getName}がガチャでGigantic☆大当たり！")(forString[IO])
              sendMessageToEveryone(message)(forTextComponent[IO])
              SendSoundEffect.sendEverySoundWithoutIgnore(
                Sound.ENTITY_ENDERDRAGON_DEATH,
                0.5f,
                2f
              )
            case Big =>
              player.playSound(player.getLocation, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1f)
              if (count == 1) player.sendMessage(s"${GOLD}おめでとう！！大当たり！$additionalMessage")
            case Regular if count == 1 =>
              player.sendMessage(s"${YELLOW}おめでとう！当たり！$additionalMessage")
            case GachaRingoOrExpBottle if count == 1 =>
              player.sendMessage(s"${WHITE}はずれ！また遊んでね！$additionalMessage")
          }
      }
    }
  }
}
