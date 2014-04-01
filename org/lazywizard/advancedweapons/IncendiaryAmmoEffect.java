package org.lazywizard.advancedweapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import org.lwjgl.util.vector.Vector2f;

public class IncendiaryAmmoEffect implements OnHitEffectPlugin
{
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
            Vector2f point, boolean shieldHit, CombatEngineAPI engine)
    {
        if (!shieldHit)
        {
            IncendiaryAmmoPlugin.startFire(target, point, // Victim, location
                    projectile.getDamageAmount(), 10f, // Burn damage, duration
                    projectile.getSource()); // Fire starter
        }
    }
}
