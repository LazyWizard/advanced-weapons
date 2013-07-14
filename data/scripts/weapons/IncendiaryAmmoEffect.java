package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.plugins.IncendiaryAmmoPlugin;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.combat.DefenseUtils;
import org.lwjgl.util.vector.Vector2f;

public class IncendiaryAmmoEffect implements OnHitEffectPlugin
{
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
            Vector2f point, boolean shieldHit, CombatEngineAPI engine)
    {
        if (!shieldHit)
        {
            // TODO: remove this before release (only here for debug purposes)
            System.out.println("In bounds: "
                    + CollisionUtils.isPointWithinBounds(point, target));
            if (target instanceof ShipAPI)
            {
                System.out.println("Armor value at impact site is "
                        + DefenseUtils.getArmorValue((ShipAPI) target, point));
            }

            IncendiaryAmmoPlugin.startFire(target, point, 500f, 10f,
                    projectile.getSource());
        }
    }
}
