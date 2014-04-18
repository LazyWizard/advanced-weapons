package org.lazywizard.advancedweapons;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.util.IntervalUtil;

public class IncendiaryBeamEffect implements BeamEffectPlugin
{
    private static final float CAUSE_FIRE_EVERY_X_SECONDS = 1f;
    private static final float FIRE_DAMAGE_TOTAL = 500f;
    private static final float FIRE_DAMAGE_DURATION = 500f;
    private CombatEntityAPI currentTarget = null, lastTarget = null;
    private final IntervalUtil fireTimer = new IntervalUtil(
            CAUSE_FIRE_EVERY_X_SECONDS, CAUSE_FIRE_EVERY_X_SECONDS);

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam)
    {
        CombatEntityAPI target = beam.getDamageTarget();
        if (target != null)
        {
            // New target? Start a fire immediately
            if (target != currentTarget && target != lastTarget)
            {
                IncendiaryAmmoPlugin.startFire(target, beam.getTo(),
                        FIRE_DAMAGE_TOTAL, FIRE_DAMAGE_DURATION, beam.getSource());
                fireTimer.setInterval(CAUSE_FIRE_EVERY_X_SECONDS,
                        CAUSE_FIRE_EVERY_X_SECONDS);
            }
            // Old target? Only start a new fire occasionally (optimization)
            else
            {
                fireTimer.advance(amount);
                if (fireTimer.intervalElapsed())
                {
                    IncendiaryAmmoPlugin.startFire(target, beam.getTo(),
                            FIRE_DAMAGE_TOTAL, FIRE_DAMAGE_DURATION, beam.getSource());
                }
            }

            // Keep track of who we are currently hitting
            currentTarget = lastTarget = target;
        }
        else
        {
            // Not hitting anyone? Reset the target
            currentTarget = null;
        }
    }
}
