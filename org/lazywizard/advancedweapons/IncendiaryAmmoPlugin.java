package org.lazywizard.advancedweapons;

import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.entities.AnchoredEntity;
import org.lwjgl.util.vector.Vector2f;

// TODO: Check for nearby fires and merge them for better performance
public class IncendiaryAmmoPlugin extends BaseEveryFrameCombatPlugin
{
    private static final float TIME_BETWEEN_DAMAGE_TICKS = .2f;
    private static final float TIME_BETWEEN_PARTICLE_TICKS = .25f;
    private static WeakReference<IncendiaryAmmoPlugin> currentInstance;
    private final Map<CombatEntityAPI, List<FireData>> activeFires = new HashMap<>();
    private float lastDamage, lastParticle;
    private boolean shouldMergeFires = false;

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
            getInstance().stopFiresActual(target);
        }
    }

    public static void stopFiresInArea(CombatEntityAPI target, Vector2f loc, float radius)
    {
        if (getInstance() != null)
        {
            getInstance().stopFiresInAreaActual(target, loc, radius);
        }
    }

    public static List<FireData> getFires(CombatEntityAPI target)
    {
        if (getInstance() != null)
        {
            return new ArrayList(getInstance().activeFires.values());
        }

        return Collections.<FireData>emptyList();
    }

    private static IncendiaryAmmoPlugin getInstance()
    {
        if (currentInstance == null || currentInstance.get() == null)
        {
            return null;
        }

        return currentInstance.get();
    }

    private void startFireActual(CombatEntityAPI target, Vector2f hitLoc,
            float totalDamage, float burnDuration, CombatEntityAPI source)
    {
        List<FireData> fires;

        if (activeFires.containsKey(target))
        {
            fires = activeFires.get(target);
            shouldMergeFires = true;
        }
        else
        {
            fires = new ArrayList<>();
            activeFires.put(target, fires);
        }

        fires.add(new FireData(target, hitLoc, totalDamage, burnDuration, source));
    }

    private void stopFiresActual(CombatEntityAPI target)
    {
        activeFires.remove(target);
    }

    private void stopFiresInAreaActual(CombatEntityAPI target, Vector2f loc, float radius)
    {
        if (activeFires.containsKey(target))
        {
            List<FireData> fires = activeFires.get(target);
            for (Iterator<FireData> iter = fires.iterator(); iter.hasNext();)
            {
                FireData tmp = iter.next();
                if (MathUtils.getDistance(loc, tmp.getLocation()) <= radius)
                {
                    iter.remove();
                }
            }

            // All fires have been put out
            if (fires.isEmpty())
            {
                activeFires.remove(target);
            }
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
        final CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused() || activeFires.isEmpty())
        {
            return;
        }

        lastDamage += amount;
        lastParticle += amount;

        boolean dealDamage = (lastDamage >= TIME_BETWEEN_DAMAGE_TICKS);
        boolean showParticle = (lastParticle >= TIME_BETWEEN_PARTICLE_TICKS);

        // Deal fire damage for all actively burning projectiles
        for (Iterator<List<FireData>> iter = activeFires.values().iterator(); iter.hasNext();)
        {
            List<FireData> fires = iter.next();

            if (fires.isEmpty())
            {
                iter.remove();
                continue;
            }

            if (shouldMergeFires && fires.size() > 1)
            {
                // TODO
                //System.out.println("Would merge fires now.");
            }

            for (Iterator<FireData> iter2 = fires.iterator(); iter2.hasNext();)
            {
                FireData fire = iter2.next();

                if (engine.getTotalElapsedTime(false) >= fire.expiration
                        || !engine.isEntityInPlay(fire.getVictim()))
                {
                    iter2.remove();
                }
                else
                {
                    if (dealDamage)
                    {
                        engine.applyDamage(fire.getVictim(), fire.getLocation(),
                                fire.dps * lastDamage, DamageType.FRAGMENTATION,
                                fire.dps * lastDamage, true, true, fire.getFireSource());
                    }

                    // Draw smoke effect to show where the fire is burning
                    if (showParticle)
                    {
                        // Vary color randomly
                        Color color = Color.DARK_GRAY;
                        engine.addSmokeParticle(fire.getLocation(), // Location
                                MathUtils.getRandomPointOnCircumference(null, 5f), // Velocity
                                MathUtils.getRandomNumberInRange(20f, 40f), // Size
                                MathUtils.getRandomNumberInRange(.05f, .15f), // Brightness
                                MathUtils.getRandomNumberInRange(2.5f, 3.5f), // Duration
                                (Math.random() > .5 ? color : color.darker())); // Color
                    }
                }
            }
        }

        if (dealDamage)
        {
            lastDamage -= TIME_BETWEEN_DAMAGE_TICKS;
        }

        if (showParticle)
        {
            lastParticle -= TIME_BETWEEN_PARTICLE_TICKS;
        }

        shouldMergeFires = false;
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        IncendiaryAmmoPlugin.currentInstance = new WeakReference<>(this);
    }

    public static class FireData
    {
        private final AnchoredEntity hitLoc;
        private final CombatEntityAPI source;
        private final float dps, expiration;

        private FireData(CombatEntityAPI target, Vector2f hitLoc,
                float totalDamage, float burnDuration, CombatEntityAPI source)
        {
            this.hitLoc = new AnchoredEntity(target, hitLoc);
            this.source = source;
            dps = totalDamage / burnDuration;
            expiration = Global.getCombatEngine().getTotalElapsedTime(false)
                    + burnDuration;
        }

        public Vector2f getLocation()
        {
            return hitLoc.getLocation();
        }

        public CombatEntityAPI getVictim()
        {
            return hitLoc.getAnchor();
        }

        public CombatEntityAPI getFireSource()
        {
            return source;
        }
    }
}
