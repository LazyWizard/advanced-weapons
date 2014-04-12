package data.scripts.weapons;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;

// Freespace 2-styled slashing beams
// This is meant for burst beams and does not obey weapon turn speed
// TODO: Fix beams 'sticking' if fired at edge of their arc (extremely exploitable!)
// TODO: Tie slash size/duration into something easily editable
// TODO: The code could use some cleanup
public class SlashBeamEffect implements BeamEffectPlugin
{
    private float relativeArcStart, arcSize, startTime,
            duration, direction;
    private boolean newBeam = true, slashDone = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam)
    {
        if (slashDone)
        {
            return;
        }

        WeaponAPI weapon = beam.getWeapon();
        ShipAPI ship = weapon.getShip();
        float curTime = engine.getTotalElapsedTime(false);

        // First frame: calculate the details of the slashing motion
        if (newBeam)
        {
            // Whether the beam travels right or left
            direction = (Math.random() > .5f ? 1f : -1f);
            // The size of the slash arc in degrees
            arcSize = 45f; //weapon.getCurrSpread();
            // The angle of the beginning of the slash
            // Ship facing is used to compensate for turning while firing
            relativeArcStart = (weapon.getCurrAngle() - (arcSize / 2f * direction))
                    - ship.getFacing();
            // The time the slash started (used to calculate slash progress)
            startTime = curTime;
            // How long the slash lasts (used to calculate slash speed)
            duration = weapon.getDerivedStats().getBurstFireDuration();
            // Only calculate all of this once per beam
            newBeam = false;
        }

        // How long the beam has been active
        float elapsed = curTime - startTime;

        // If beam is finished, move weapon back to original facing
        if (elapsed >= (duration - .1f))
        {
            weapon.setCurrAngle(MathUtils.clampAngle(relativeArcStart +
                    (arcSize / 2f * direction) + ship.getFacing()));
            slashDone = true;
        }
        // Otherwise, continue the slashing motion
        else
        {
            weapon.setCurrAngle(MathUtils.clampAngle((relativeArcStart
                    + (arcSize * (elapsed / duration) * direction))
                    + ship.getFacing()));
        }
    }
}
