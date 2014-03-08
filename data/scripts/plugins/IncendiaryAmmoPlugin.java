package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.entities.AnchoredEntity;
import org.lwjgl.util.vector.Vector2f;

// TODO: Check for nearby fires and merge them for better performance
public class IncendiaryAmmoPlugin implements EveryFrameCombatPlugin
{
    // How long between damage/particle effect ticks
    private static final float TIME_BETWEEN_DAMAGE_TICKS = .2f;
    private static final float TIME_BETWEEN_PARTICLE_TICKS = .8f;
    // Stores the currently burning fires
    // Having the Set backed by a WeakHashMap helps prevent memory leaks
    private static final Set burning = Collections.newSetFromMap(new WeakHashMap());
    private CombatEngineAPI engine;
    private float lastDamage, lastParticle;

    @Override
    public void advance(float amount, List events)
    {
        if (engine.isPaused() || burning.isEmpty())
        {
            return;
        }

        lastDamage += amount;
        lastParticle += amount;

        float damageMod = lastDamage;

        boolean dealDamage = false;
        if (lastDamage >= TIME_BETWEEN_DAMAGE_TICKS)
        {
            lastDamage -= TIME_BETWEEN_DAMAGE_TICKS;
            dealDamage = true;
        }

        boolean showParticle = false;
        if (lastParticle >= TIME_BETWEEN_PARTICLE_TICKS)
        {
            lastParticle -= TIME_BETWEEN_PARTICLE_TICKS;
            showParticle = true;
        }

        // Deal fire damage for all actively burning projectiles
        for (Iterator iter = burning.iterator(); iter.hasNext();)
        {
            FireData fire = (FireData) iter.next();

            // Check if the fire has gone out
            if (engine.getTotalElapsedTime(false) >= fire.expiration
                    || !engine.isEntityInPlay(fire.getAnchor()))
            {
                iter.remove();
            }
            else
            {
                if (dealDamage)
                {
                    engine.applyDamage(fire.getAnchor(), fire.getLocation(),
                            fire.dps * damageMod, DamageType.FRAGMENTATION,
                            fire.dps * damageMod, true, true, fire.source.get());
                }

                // Draw smoke effect to show where the fire is burning
                if (showParticle)
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
        // TODO: merge with nearby fires on the same target
        burning.add(new FireData(target, hitLoc, totalDamage, burnDuration, source));
    }

    public static void stopFire(CombatEntityAPI target)
    {
        for (Iterator iter = burning.iterator(); iter.hasNext();)
        {
            FireData tmp = (FireData) iter.next();
            if (target == tmp.getAnchor())
            {
                iter.remove();
            }
        }
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
        burning.clear();
    }

    private static class FireData
    {
        private final AnchoredEntity hitLoc;
        private final WeakReference source;
        private final float dps, expiration;

        public FireData(CombatEntityAPI target, Vector2f hitLoc,
                float totalDamage, float burnDuration, CombatEntityAPI source)
        {
            this.hitLoc = new AnchoredEntity(target, hitLoc);
            this.source = new WeakReference(source);
            dps = totalDamage / burnDuration;
            expiration = Global.getCombatEngine().getTotalElapsedTime(false)
                    + burnDuration;
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
