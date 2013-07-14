package data.scripts.plugins;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;

// TODO: Check for nearby fires and merge them for better performance
public class IncendiaryAmmoPlugin implements EveryFrameCombatPlugin
{
    private static List burning = new ArrayList();
    private CombatEngineAPI engine;

    @Override
    public void advance(float amount, List events)
    {
        // Obvious exploit is obvious
        if (engine.isPaused())
        {
            return;
        }

        // Deal fire damage for all actively burning projectiles
        FireData fire;
        for (Iterator iter = burning.iterator(); iter.hasNext();)
        {
            fire = (FireData) iter.next();

            // Check if the fire has gone out
            if (CombatUtils.getElapsedCombatTime() > fire.expiration
                    || !engine.isEntityInPlay(fire.getAnchor()))
            {
                iter.remove();
            }
            else
            {
                engine.applyDamage(fire.getAnchor(), fire.getLocation(),
                        fire.dps * amount, DamageType.FRAGMENTATION,
                        fire.dps * amount, true, true, fire.source);

                // Draw smoke effect to show where the fire is burning
                if (Math.random() > .9)
                {
                    engine.addSmokeParticle(fire.getLocation(), // Location
                            MathUtils.getRandomPointOnCircumference(null, 5f), // Velocity
                            MathUtils.getRandomNumberInRange(20f, 40f), // Size
                            MathUtils.getRandomNumberInRange(.05f, .15f), // Brightness
                            3f, Color.DARK_GRAY); // Duration, color
                }
            }
        }
    }

    public static void startFire(CombatEntityAPI target, Vector2f hitLoc,
            float totalDamage, float burnDuration, CombatEntityAPI source)
    {
        burning.add(new FireData(target, hitLoc, totalDamage, burnDuration, source));
        System.out.println("Started fire dealing " + totalDamage
                + " frag damage over the next " + burnDuration + " seconds");
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
        burning.clear();
    }

    private static class FireData
    {
        private SimpleEntity hitLoc;
        private CombatEntityAPI source;
        private float dps, expiration;

        public FireData(CombatEntityAPI target, Vector2f hitLoc,
                float totalDamage, float burnDuration, CombatEntityAPI source)
        {
            this.hitLoc = new SimpleEntity(hitLoc, target);
            this.source = source;
            dps = totalDamage / burnDuration;
            expiration = CombatUtils.getElapsedCombatTime() + burnDuration;
        }

        public Vector2f getLocation()
        {
            return hitLoc.getLocation();
        }

        public CombatEntityAPI getAnchor()
        {
            return hitLoc.getAnchor();
        }
    }
}
