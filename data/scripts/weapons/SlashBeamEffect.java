package data.scripts.weapons;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;

// TODO: Reset weapon facing at end of beam (if possible)
// TODO: Fix beams 'sticking' if fired at edge of their arc (exploitable!)
// TODO: Tie slash size/duration into something easily editable
public class SlashBeamEffect implements BeamEffectPlugin
{
    private float relativeArcStart, arcSize, startTime,
            duration, direction;
    private boolean newBeam = true;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam)
    {
        WeaponAPI weapon = beam.getWeapon();
        float curTime = engine.getTotalElapsedTime(false);

        // First frame: calculate the details of the slash
        if (newBeam)
        {
            // Whether the beam travels right or left
            direction = (Math.random() > .5f ? 1f : -1f);
            // The size of the slash arc in degrees
            arcSize = 45f; //weapon.getCurrSpread();
            // The angle of the beginning of the slash
            // Ship facing is used to compensate for turning while firing
            relativeArcStart = (weapon.getCurrAngle() - (arcSize / 2f * direction))
                    - weapon.getShip().getFacing();
            // The time the slash started (used to calculate slash progress)
            startTime = curTime;
            // How long the slash lasts (used to calculate slash speed)
            duration = weapon.getDerivedStats().getBurstFireDuration();
            // Only calculate all of this once per beam
            newBeam = false;
        }

        // Percentage of the slash that is complete
        float slashProgress = (curTime - startTime) / duration;
        System.out.println("Slash progress: " + (slashProgress * 100f) + "%");
        weapon.setCurrAngle(MathUtils.clampAngle((relativeArcStart
                + (arcSize * slashProgress * direction)) + weapon.getShip().getFacing()));
    }
}
