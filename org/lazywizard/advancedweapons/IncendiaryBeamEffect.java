package org.lazywizard.advancedweapons;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.util.IntervalUtil;

public class IncendiaryBeamEffect implements BeamEffectPlugin
{
    // How frequently to start a fire (higher = less lag, less responsiveness)
    private static final float CAUSE_FIRE_EVERY_X_SECONDS = 1f;
    // For every second you hold the beam on a target,
    // this amount of fire damage will be dealt over time
    private static final float DAMAGE_PER_SECOND_HELD = 150f;
    // How long the fire will be active, in seconds
    // The damage amount will be spread out over this time (lower = more DPS)
    private static final float FIRE_DURATION = 5f;
    private CombatEntityAPI lastTarget = null;
    private final IntervalUtil fireTimer = new IntervalUtil(
            CAUSE_FIRE_EVERY_X_SECONDS, CAUSE_FIRE_EVERY_X_SECONDS);

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam)
    {
        CombatEntityAPI target = beam.getDamageTarget();
        if (target != null)
        {
            // New target = new fire countdown
            if (target != lastTarget)
            {
                //System.out.println("Starting countdown for " + target);
                fireTimer.setInterval(CAUSE_FIRE_EVERY_X_SECONDS,
                        CAUSE_FIRE_EVERY_X_SECONDS);
            }

            // Keep track of who we are currently hitting
            lastTarget = target;

            // Only cause fires occasionally (reduces lag)
            fireTimer.advance(amount);
            if (fireTimer.intervalElapsed())
            {
                //System.out.println("Starting fire on " + target);
                IncendiaryAmmoPlugin.startFire(target, beam.getTo(),
                        DAMAGE_PER_SECOND_HELD * CAUSE_FIRE_EVERY_X_SECONDS,
                        FIRE_DURATION, beam.getSource());
            }
        }
    }
}
