package data.scripts.weapons;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import java.awt.Color;
import org.lazywizard.lazylib.combat.WeaponUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;

public class ThorEffect implements BeamEffectPlugin
{
    // What color is the core of the arc?
    private static final Color CORE_COLOR = new Color(255, 255, 255, 255);
    // What color is the fringe of the arc?
    private static final Color FRINGE_COLOR = Color.YELLOW; //new Color(85, 25, 215, 255);
    // How long since the last arc (used for DPS calculations)
    private float timeSinceLastArc = 0f;
    // The current damaging and decorative (non-targeted) arcs
    private CombatEntityAPI activeArc = null, decorativeArc = null;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam)
    {
        // Only have one EMP arc active at a time
        if (activeArc == null || !engine.isEntityInPlay(activeArc))
        {
            // Beam hit something - send lightning at it!
            if (beam.getDamageTarget() != null)
            {
                if (decorativeArc != null && engine.isEntityInPlay(decorativeArc))
                {
                    engine.removeEntity(decorativeArc);
                }

                float damage = timeSinceLastArc * beam.getWeapon().getDerivedStats().getDps();
                float emp = timeSinceLastArc * beam.getWeapon().getDerivedStats().getEmpPerSecond();
                timeSinceLastArc = 0f;
                activeArc = engine.spawnEmpArc(beam.getSource(), beam.getFrom(),
                        beam.getSource(), beam.getDamageTarget(),
                        DamageType.ENERGY, damage, emp,
                        beam.getWeapon().getRange(),
                        "tachyon_lance_emp_impact", 15f,
                        FRINGE_COLOR, CORE_COLOR);
            }
            // Beam missed - send lightning anyway!
            else if (decorativeArc == null || !engine.isEntityInPlay(decorativeArc))
            {
                timeSinceLastArc = 0f;
                decorativeArc = engine.spawnEmpArc(beam.getSource(), beam.getFrom(),
                        beam.getSource(), new SimpleEntity(beam.getTo()),
                        DamageType.ENERGY, 0f, 0f,
                        beam.getWeapon().getRange(),
                        "tachyon_lance_emp_impact", 15f,
                        FRINGE_COLOR, CORE_COLOR);
            }
            else
            {
                timeSinceLastArc += amount;
            }
        }
    }
}
