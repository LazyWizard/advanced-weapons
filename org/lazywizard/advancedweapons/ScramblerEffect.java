package org.lazywizard.advancedweapons;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import java.awt.Color;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

// This is designed for burst beams
public class ScramblerEffect implements BeamEffectPlugin
{
    private static final float SCRAMBLE_CHANCE = .25f;
    private final Set<MissileAPI> scrambled = new HashSet<>();

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam)
    {
        // Remove expired projectiles
        for (Iterator<MissileAPI> iter = scrambled.iterator(); iter.hasNext();)
        {
            if (!engine.isEntityInPlay(iter.next()))
            {
                iter.remove();
            }
        }

        // Check if we hit a missile this frame
        CombatEntityAPI target = beam.getDamageTarget();
        if (target != null && target instanceof MissileAPI)
        {
            MissileAPI missile = (MissileAPI) target;

            // Apply extra damage to unguided missiles
            if (missile.isFizzling())
            {
                engine.applyDamage(missile, missile.getLocation(),
                        beam.getWeapon().getDerivedStats().getDps() * amount,
                        beam.getWeapon().getDamageType(),
                        beam.getWeapon().getDerivedStats().getEmpPerShot(),
                        false, true, beam.getSource());
                return;
            }

            // Only one chance to scramble a missile per beam
            if (scrambled.contains(missile))
            {
                return;
            }

            scrambled.add(missile);

            // Check if this missile will be scrambled
            if (Math.random() < SCRAMBLE_CHANCE)
            {
                missile.setOwner(beam.getSource().getOwner());
                missile.setSource(beam.getSource());
                engine.spawnExplosion(missile.getLocation(), missile.getVelocity(),
                        Color.GREEN, 15f, .85f);
            }
        }
    }
}
