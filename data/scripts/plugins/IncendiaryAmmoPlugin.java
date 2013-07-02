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

public class IncendiaryAmmoPlugin implements EveryFrameCombatPlugin
{
    private static Color[] FIRE_COLORS =
    {
        Color.RED, Color.YELLOW, Color.ORANGE, Color.ORANGE, Color.YELLOW
    };
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
        float sparkVelocity;
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
                sparkVelocity = MathUtils.getRandomNumberInRange(50f, 200f);
                engine.applyDamage(fire.getAnchor(), fire.getLocation(),
                        fire.dps * amount, DamageType.FRAGMENTATION,
                        fire.dps * amount, true, true, fire.source);
                engine.addSmokeParticle(fire.getLocation(),
                        MathUtils.getPointOnCircumference(null, sparkVelocity,
                        (float) (Math.random() * 360f)),
                        MathUtils.getRandomNumberInRange(1f, 3f), .75f, 1f,
                        FIRE_COLORS[(int) (Math.random() * FIRE_COLORS.length)]);
            }
        }
    }

    public static void startFire(CombatEntityAPI target, Vector2f hitLoc,
            float totalDamage, float burnDuration, CombatEntityAPI source)
    {
        burning.add(new FireData(target, hitLoc, totalDamage, burnDuration, source));
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
