package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.entities.AnchoredEntity;
import org.lwjgl.util.vector.Vector2f;

// TODO: Check for nearby fires and merge them for better performance
public class IncendiaryAmmoPlugin implements EveryFrameCombatPlugin
{
    private static final float TIME_BETWEEN_DAMAGE_TICKS = .2f;
    private static final float TIME_BETWEEN_PARTICLE_TICKS = .4f;
    private static WeakReference currentInstance;
    // Stores the currently burning fires
    // Using a WeakHashMap helps prevent memory leaks
    private final Map burning = new WeakHashMap();
    private boolean shouldMergeFires = false;
    private CombatEngineAPI engine;
    private float lastDamage, lastParticle;

    public static void startFire(CombatEntityAPI target, Vector2f hitLoc,
            float totalDamage, float burnDuration, CombatEntityAPI source)
    {
        if (getInstance() != null)
        {
            getInstance().startFireActual(target, hitLoc, totalDamage, burnDuration, source);
        }
    }

    public static void stopFires(CombatEntityAPI target)
    {
        if (getInstance() != null)
        {
            getInstance().stopFires(target);
        }
    }

    private static void stopFiresInArea(CombatEntityAPI target, Vector2f loc, float radius)
    {
        if (getInstance() != null)
        {
            getInstance().stopFiresInArea(target, loc, radius);
        }
    }

    private static IncendiaryAmmoPlugin getInstance()
    {
        if (currentInstance == null || currentInstance.get() == null)
        {
            return null;
        }

        return (IncendiaryAmmoPlugin) currentInstance.get();
    }

    private void startFireActual(CombatEntityAPI target, Vector2f hitLoc,
            float totalDamage, float burnDuration, CombatEntityAPI source)
    {
        List fires;

        if (burning.containsKey(target))
        {
            fires = (List) burning.get(target);
            shouldMergeFires = true;
        }
        else
        {
            fires = new ArrayList();
            burning.put(target, fires);
        }

        // TODO: merge with nearby fires on the same target
        fires.add(new FireData(target, hitLoc, totalDamage, burnDuration, source));
    }

    private void stopFiresActual(CombatEntityAPI target)
    {
        if (burning.containsKey(target))
        {
            List fires = (List) burning.get(target);
            for (Iterator iter = fires.iterator(); iter.hasNext();)
            {
                FireData tmp = (FireData) iter.next();
                iter.remove();
            }
        }
    }

    // TODO
    private void stopFiresInAreaActual(CombatEntityAPI target, Vector2f loc, float radius)
    {

    }

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
        for (Iterator iter = burning.values().iterator(); iter.hasNext();)
        {
            List fires = (List) iter.next();
            if (fires.isEmpty())
            {
                iter.remove();
                continue;
            }

            if (shouldMergeFires && fires.size() > 1)
            {
                // TODO
                System.out.println("Would merge fires now.");
            }

            for (Iterator iter2 = fires.iterator(); iter2.hasNext();)
            {
                FireData fire = (FireData) iter2.next();

                if (engine.getTotalElapsedTime(false) >= fire.expiration
                        || !engine.isEntityInPlay(fire.getAnchor()))
                {
                    iter2.remove();
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

        shouldMergeFires = false;
    }

    @Override
    public void init(CombatEngineAPI engine)
    {;
        this.engine = engine;
        currentInstance = new WeakReference(this);
    }

    private static class FireData
    {
        private final AnchoredEntity hitLoc;
        private final WeakReference source;
        private final float dps, expiration;

        private FireData(CombatEntityAPI target, Vector2f hitLoc,
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
