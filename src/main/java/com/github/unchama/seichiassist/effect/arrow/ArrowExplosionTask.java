package com.github.unchama.seichiassist.effect.arrow;

import com.github.unchama.seichiassist.SeichiAssist;
import com.github.unchama.seichiassist.effect.FixedMetadataValueHolder;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.util.Vector;

public class ArrowExplosionTask extends AbstractEffectTask<SmallFireball> {
	long tick;

	public ArrowExplosionTask(Player player) {
		this.tick = 0;

		//発射する音を再生する.
		player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1, 1.3f);

		//スキルを実行する処理
		Location loc = player.getLocation().clone();
		loc.add(loc.getDirection()).add(getAddtionalVector());
		Vector vec = loc.getDirection();
		vec.multiply(getVectorMultipier());
		projectile = player.getWorld().spawn(loc, SmallFireball.class);
		SeichiAssist.entitylist.add(projectile);
		projectile.setShooter(player);
		projectile.setGravity(false);
		//読み込み方法
		/*
		 * Projectile proj = event.getEntity();
			if ( proj instanceof Arrow && proj.hasMetadata("ArrowSkill") ) {
			}
		 */
		projectile.setMetadata("ArrowSkill", FixedMetadataValueHolder.TRUE);
		projectile.setVelocity(vec);
	}

	@Override
	public void run() {
		tick ++;
		if(tick > 100){
			projectile.remove();
			SeichiAssist.entitylist.remove(projectile);
			this.cancel();
		}
	}

	public Vector getAddtionalVector() {
		return new Vector(0, 1.6, 0);
	}

	public double getVectorMultipier() {
		return 0.4;
	}
}
