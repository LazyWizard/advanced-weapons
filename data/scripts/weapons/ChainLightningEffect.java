package data.scripts.weapons;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.Color;
import java.util.*;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.WeaponUtils;
import org.lwjgl.util.vector.Vector2f;

public class ChainLightningEffect implements BeamEffectPlugin
{
    // If true, don't hit the same ship twice in the same burst
    private static final boolean IGNORE_SAME_SHIP_IN_BURST = true;
    // If true, pick a random enemy in range to be the next link in the chain
    private static final boolean PICK_RANDOM_ENEMY_IN_RANGE = false;
    // How much of its previous max length will each chain retain?
    private static final float RANGE_RETENTION_PER_CHAIN = .75f;
    // How much damage will each chain do, compared to the previous?
    private static final float DAMAGE_RETENTION_PER_CHAIN = .85f;
    // How many chains should we limit the weapon to generating?
    private static final int MAXIMUM_CHAINS_PER_BURST = 5;
    // What color is the core of the arc?
    private static final Color CORE_COLOR = new Color(255, 255, 255, 255);
    // What color is the fringe of the arc?
    private static final Color FRINGE_COLOR = new Color(85, 25, 215, 255);
    // How long since the last arc (used for DPS calculations)
    private float timeSinceLastArc = 0f;
    // The last arc created by this beam
    private CombatEntityAPI activeArc = null;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam)
    {
        CombatEntityAPI target = beam.getDamageTarget();

        // Only have one EMP arc active at a time
        if (activeArc == null || !engine.isEntityInPlay(activeArc))
        {
            // Check that we hit something and that it wasn't a shield hit
            if (target != null && target instanceof ShipAPI && (target.getShield() == null
                    || !target.getShield().isWithinArc(beam.getTo())))
            {
                // Count how many links are in the chain so far
                int numStrikes = 0;

                // Source of the current lightning chain
                Vector2f source = beam.getFrom();
                CombatEntityAPI currentEmitter = beam.getSource();
                // Victim of the current lightning chain
                CombatEntityAPI currentVictim = target;

                float range = beam.getWeapon().getRange();
                // Ensure we keep the same DPS as listed in the weapon's stats tooltip
                float damage = timeSinceLastArc
                        * WeaponUtils.calculateDamagePerSecond(beam.getWeapon());
                float emp = timeSinceLastArc
                        * beam.getWeapon().getDerivedStats().getEmpPerSecond();

                // This is used to prevent hitting the same target twice
                // if IGNORE_SAME_SHIP_IN_BURST is set to true
                Set struck = new HashSet();

                do
                {
                    // Spawn this chain's lightning arc
                    activeArc = engine.spawnEmpArc(beam.getSource(), source,
                            currentEmitter, currentVictim,
                            DamageType.ENERGY, damage, emp, range,
                            "tachyon_lance_emp_impact", 15f,
                            FRINGE_COLOR, CORE_COLOR);
                    currentEmitter = currentVictim;

                    // Check that we haven't hit our chain limit
                    if (++numStrikes >= MAXIMUM_CHAINS_PER_BURST)
                    {
                        return;
                    }

                    // Reduce the stats of the next chain
                    range *= RANGE_RETENTION_PER_CHAIN;
                    damage *= DAMAGE_RETENTION_PER_CHAIN;
                    emp *= DAMAGE_RETENTION_PER_CHAIN;

                    // Find our next victim
                    source = currentVictim.getLocation();
                    List enemies = AIUtils.getNearbyAllies(currentVictim, range);
                    enemies.remove(currentVictim);

                    // Remove enemies that have already been struck once
                    // (only if IGNORE_SAME_SHIP_IN_BURST is true)
                    if (IGNORE_SAME_SHIP_IN_BURST)
                    {
                        struck.add(currentVictim);
                        enemies.removeAll(struck);
                    }

                    // Remove enemies who would block or avoid a strike
                    ShipAPI tmp;
                    for (Iterator iter = enemies.iterator(); iter.hasNext();)
                    {
                        tmp = (ShipAPI) iter.next();
                        if ((tmp.getShield() != null && tmp.getShield().isOn()
                                && tmp.getShield().isWithinArc(source))
                                || (tmp.getPhaseCloak() != null
                                && tmp.getPhaseCloak().isActive()))
                        {
                            iter.remove();
                        }
                    }

                    // Pick a random valid enemy in range
                    if (!enemies.isEmpty())
                    {
                        if (PICK_RANDOM_ENEMY_IN_RANGE)
                        {
                            currentVictim = (ShipAPI) enemies.get((int) (Math.random() * enemies.size()));
                        }
                        else
                        {
                            ShipAPI closest = null;
                            float distance, closestDistance = Float.MAX_VALUE;

                            // Find the closest enemy in range
                            for (int x = 0; x < enemies.size(); x++)
                            {
                                tmp = (ShipAPI) enemies.get(x);
                                distance = MathUtils.getDistance(tmp, currentVictim);

                                // This ship is closer than the previous best
                                if (distance < closestDistance)
                                {
                                    closest = tmp;
                                    closestDistance = distance;
                                }
                            }

                            currentVictim = closest;
                        }
                    }
                    else
                    {
                        // No enemies in range, end the chain
                        currentVictim = null;
                    }
                }
                while (currentVictim != null);
            }
            // Beam missed - send lightning anyway!
            else
            {
                timeSinceLastArc = 0f;
                activeArc = engine.spawnEmpArc(beam.getSource(), beam.getFrom(),
                        beam.getSource(), new FakeEntity(beam.getTo()),
                        DamageType.ENERGY, 0f, 0f,
                        beam.getWeapon().getRange(),
                        "tachyon_lance_emp_impact", 15f,
                        FRINGE_COLOR, CORE_COLOR);
            }
        }
    }
}
