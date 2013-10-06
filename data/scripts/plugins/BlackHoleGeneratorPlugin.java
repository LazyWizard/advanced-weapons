package data.scripts.plugins;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

// TODO: Change calculations to use time elapsed instead of number of frames
public class BlackHoleGeneratorPlugin implements EveryFrameCombatPlugin
{
    /** Whether to show debug particles in and around the black holes */
    private static final boolean RENDER_DEBUG = false;
    /** The projectile ID of the shell that creates black holes */
    private static final String SHELL_ID = "lw_bhg_shot";
    /** How long the shell stays on the map before exploding */
    private static final float EXPLOSION_DELAY = 5f;
    /** How long the black hole lasts before collapsing */
    private static final float EXPLOSION_DURATION = 30f;
    /** If a black hole is generated via impact, multiply it by this */
    private static final float EXPLOSION_HIT_MODIFIER = .25f;
    /** How often to create damage points (higher = more damage, less coverage) */
    private static final int FRAMES_PER_DAMAGE_POINT = 4;
    /** How far the pull effect of the hole extends */
    private static final float GRAVITY_WELL_RADIUS = 450f;
    /** How many gravity well particles to create per frame */
    private static final int GRAVITY_WELL_PARTICLE_DENSITY = 2;
    /** How opaque the gravity well particles should be */
    private static final float GRAVITY_WELL_PARTICLE_OPACITY = .5f;
    /** The color of the gravity well particle effects */
    private static final Color GRAVITY_WELL_PARTICLE_COLOR = new Color(93, 36, 145);
    /** The size of the part of the black hole that actually causes damage */
    private static final float DAMAGE_ZONE_RADIUS = GRAVITY_WELL_RADIUS / 3f;
    /** How much damage should be spread over the entire damage zone per second */
    private static final float DAMAGE_ZONE_DPS = 900f;
    /** How much damage can be blocked by shield */
    private static final float DAMAGE_ZONE_BLOCKABLE_PERCENT = .5f;
    /** How much EMP damage should be spread over the entire damage zone per second */
    private static final float DAMAGE_ZONE_EMP_DPS = 50f;
    /** How many core particles to create per frame */
    private static final int DAMAGE_ZONE_PARTICLE_DENSITY = 1;
    /** How opaque the damage zone particles should be */
    private static final float DAMAGE_ZONE_PARTICLE_OPACITY = .5f;
    /** The color of the damage zone particle effects */
    private static final Color DAMAGE_ZONE_PARTICLE_COLOR = new Color(68, 35, 186);
    /** Max change in velocity per second towards the core for trapped entities */
    private static final float PULL_STRENGTH = 300f;
    /** Pull strength modifier vs fighters */
    private static final float STRENGTH_VS_FIGHTER = 1.5f;
    /** Pull strength modifier vs frigates */
    private static final float STRENGTH_VS_FRIGATE = 1f;
    /** Pull strength modifier vs destroyers */
    private static final float STRENGTH_VS_DESTROYER = .8f;
    /** Pull strength modifier vs cruisers */
    private static final float STRENGTH_VS_CRUISER = .5f;
    /** Pull strength modifier vs capital ships */
    private static final float STRENGTH_VS_CAPITAL = .3f;
    /** How often to update the time remaining text */
    private static final float RENDER_TEXT_INTERVAL = 1f;
    /** The color of the countdown text */
    private static final Color RENDER_TEXT_COLOR = Color.CYAN;
    /** How long before the hole expires the particles start to fade */
    private static final float FADE_TIME = 3f;
    /** A Vector2f constant of (0,0) for optimization purposes */
    private static final Vector2f NULLVEL = new Vector2f(0, 0);
    /** The current combat engine */
    private CombatEngineAPI engine;
    /** Time since the battle started */
    private float curTime = 0f;
    /** How many frames since the battle started */
    private int numUpdates = 0;
    /** When to next render floating text */
    private float nextRender = RENDER_TEXT_INTERVAL;
    /** Tracks shells - key = projectile, value = time to explode */
    private Map projs = new HashMap();
    /** Tracks holes - key = location, value = time to expire */
    private Map holes = new HashMap();

    private void applyDamage(Vector2f center, CombatEntityAPI victim, float time)
    {
        // Get a random point in the core of the black hole
        Vector2f point = MathUtils.getRandomPointInCircle(center,
                DAMAGE_ZONE_RADIUS);

        // Check if the point would hit the victim
        if (MathUtils.isPointWithinCircle(point, victim.getLocation(),
                victim.getCollisionRadius()))
        {
            // Many tiny points = increases armor reduction, uniform damage
            engine.applyDamage(victim, point, (DAMAGE_ZONE_DPS * time
                    * FRAMES_PER_DAMAGE_POINT) * (1 - DAMAGE_ZONE_BLOCKABLE_PERCENT),
                    DamageType.HIGH_EXPLOSIVE, 0f, true, true, null);
            // Part of the damage + all of the EMP can be blocked by shields
            engine.applyDamage(victim, point, (DAMAGE_ZONE_DPS * time
                    * FRAMES_PER_DAMAGE_POINT) * DAMAGE_ZONE_BLOCKABLE_PERCENT,
                    DamageType.KINETIC, DAMAGE_ZONE_EMP_DPS * time * FRAMES_PER_DAMAGE_POINT,
                    false, false, null);

            if (RENDER_DEBUG)
            {
                // Render point damage was applied at (for debug purposes)
                engine.addHitParticle(point, NULLVEL, 5f, 1.0f, 1f, Color.PINK);
            }
        }
    }

    // 2 particles per frame * 60 frames per second * .75 second duration (avg)
    // equals around 90 particles on screen at a time for each black hole
    // Add another 200 particles if RENDER_DEBUG is set to true
    private void renderHole(Vector2f center, float remaining)
    {
        // Fade away when about to expire
        float glowStrength = (remaining > FADE_TIME ? 1f : remaining / FADE_TIME);

        // How far from the edge the particle should spawn
        float fraction = .5f + ((float) Math.random() / 2f);
        Vector2f particlePos, particleVel;

        // Render particles for gravity well
        for (int x = 0; x < GRAVITY_WELL_PARTICLE_DENSITY; x++)
        {
            particlePos = MathUtils.getRandomPointOnCircumference(center,
                    GRAVITY_WELL_RADIUS * fraction);
            particleVel = Vector2f.sub(center, particlePos, null);
            engine.addSmokeParticle(particlePos, particleVel, 5f,
                    GRAVITY_WELL_PARTICLE_OPACITY * glowStrength,
                    fraction, GRAVITY_WELL_PARTICLE_COLOR);
        }

        // Render particles for damage zone
        for (int x = 0; x < DAMAGE_ZONE_PARTICLE_DENSITY; x++)
        {
            particlePos = MathUtils.getRandomPointOnCircumference(center,
                    DAMAGE_ZONE_RADIUS * fraction);
            particleVel = Vector2f.sub(center, particlePos, null);
            engine.addSmokeParticle(particlePos, particleVel, 5f,
                    DAMAGE_ZONE_PARTICLE_OPACITY * glowStrength,
                    fraction, DAMAGE_ZONE_PARTICLE_COLOR);
        }

        // Spawn 'clouds' in the center of the hole
        if (numUpdates % 20 == 0)
        {
            engine.spawnExplosion(center, NULLVEL,
                    (Math.random() >= .5 ? GRAVITY_WELL_PARTICLE_COLOR : DAMAGE_ZONE_PARTICLE_COLOR),
                    DAMAGE_ZONE_RADIUS / 3 + (float) (Math.random()
                    * DAMAGE_ZONE_RADIUS / 10) - DAMAGE_ZONE_RADIUS / 20,
                    remaining / 2);
        }

        // Render center point and circles around gravity well and damage zone
        if (RENDER_DEBUG && (numUpdates % 60 == 0))
        {
            engine.addSmokeParticle(center, NULLVEL, 5f, .5f, 2f, Color.CYAN);

            List points = MathUtils.getPointsAlongCircumference(center, GRAVITY_WELL_RADIUS, 90, 0f);
            for (int x = 0; x < points.size(); x++)
            {
                engine.addSmokeParticle((Vector2f) points.get(x), NULLVEL, 5f, .5f, 2f, Color.CYAN);
            }
            points = MathUtils.getPointsAlongCircumference(center, DAMAGE_ZONE_RADIUS, 50, 0f);
            for (int x = 0; x < points.size(); x++)
            {
                engine.addSmokeParticle((Vector2f) points.get(x), NULLVEL, 5f, .5f, 2f, Color.CYAN);
            }
        }
    }

    private void renderProjs()
    {
        Map.Entry tmp;
        DamagingProjectileAPI proj;
        float remaining;

        // Iterate through all projectiles that haven't exploded yet
        for (Iterator iter = projs.entrySet().iterator(); iter.hasNext();)
        {
            // Get the remaining time until explosion
            tmp = (Map.Entry) iter.next();
            remaining = (Float) tmp.getValue();
            remaining -= curTime;

            // Don't render countdown for exploding projectiles
            if (remaining <= 0)
            {
                continue;
            }

            // Add text counting down how long until the projectile explodes
            proj = (DamagingProjectileAPI) tmp.getKey();
            engine.addFloatingText(proj.getLocation(), "" + (int) remaining,
                    25f, RENDER_TEXT_COLOR, proj, 0f, 0f);
        }
    }

    private static float getPullStrengthMod(CombatEntityAPI victim)
    {
        float mod = 1.0f;

        if (victim instanceof ShipAPI)
        {
            ShipAPI ship = (ShipAPI) victim;
            // Modify pull strength based on ship class
            if (ship.getHullSize() == HullSize.FIGHTER)
            {
                mod *= STRENGTH_VS_FIGHTER;
            }
            else if (ship.getHullSize() == HullSize.FRIGATE)
            {
                mod *= STRENGTH_VS_FRIGATE;
            }
            else if (ship.getHullSize() == HullSize.DESTROYER)
            {
                mod *= STRENGTH_VS_DESTROYER;
            }
            else if (ship.getHullSize() == HullSize.CRUISER)
            {
                mod *= STRENGTH_VS_CRUISER;
            }
            else if (ship.getHullSize() == HullSize.CAPITAL_SHIP)
            {
                mod *= STRENGTH_VS_CAPITAL;
            }
        }

        return mod;
    }

    private static void pull(Vector2f center, CombatEntityAPI victim, float time)
    {
        Vector2f velocity, direction;
        float distance, maxVelocity = 500f;

        velocity = victim.getVelocity();
        distance = MathUtils.getDistance(victim, center);
        // Normalized directional vector
        direction = MathUtils.getDirectionalVector(victim, center);

        // TODO: replace linear decay with a curve, factor in mass
        float strength = (1f - distance / GRAVITY_WELL_RADIUS) * PULL_STRENGTH;
        strength *= getPullStrengthMod(victim);

        // Modify the velocity towards the center of the hole
        velocity.set(velocity.x + (direction.x * strength * time),
                velocity.y + (direction.y * strength * time));

        // Cap the maximum velocity
        // Avoiding a costly sqrt here, so that's why this looks awkward
        if (velocity.lengthSquared() > maxVelocity * maxVelocity)
        {
            velocity.normalise();
            velocity.set(velocity.x * maxVelocity, velocity.y * maxVelocity);
        }
    }

    private void updateHoles(float time)
    {
        Map.Entry tmp;
        Vector2f center;
        float remaining;
        CombatEntityAPI victim;

        // Iterate through all active black holes on the map
        for (Iterator iter = holes.entrySet().iterator(); iter.hasNext();)
        {
            tmp = (Map.Entry) iter.next();
            remaining = (Float) tmp.getValue() - curTime;

            // Remove black holes that have faded away
            if (remaining <= 0)
            {
                iter.remove();
                continue;
            }

            center = (Vector2f) tmp.getKey();

            // Find entities within range of the gravity effect
            for (Iterator nearby = CombatUtils.getEntitiesWithinRange(center,
                    GRAVITY_WELL_RADIUS).iterator(); nearby.hasNext();)
            {
                victim = (CombatEntityAPI) nearby.next();

                // Pull the victim towards the center
                pull(center, victim, time);

                // Limit the frequency of damage updates
                if (numUpdates % FRAMES_PER_DAMAGE_POINT == 0)
                {
                    // Apply damage to entities within the core that have hulls
                    if (((victim instanceof ShipAPI) || (victim instanceof MissileAPI))
                            && MathUtils.getDistance(victim, center)
                            < DAMAGE_ZONE_RADIUS + victim.getCollisionRadius())
                    {
                        applyDamage(center, victim, time);
                    }
                }
            }

            // Render the particle/cloud effects
            renderHole(center, remaining);
        }
    }

    private void scanForProjs()
    {
        DamagingProjectileAPI toCheck;

        // Iterate through all projectiles currently on the field
        for (Iterator allProjectiles = engine.getProjectiles().iterator();
                allProjectiles.hasNext();)
        {
            toCheck = (DamagingProjectileAPI) allProjectiles.next();

            // Check if the projectile is the type that spawns black holes
            if (SHELL_ID.equals(toCheck.getProjectileSpecId()))
            {
                // Check if this projectile is registered yet
                if (projs.containsKey(toCheck))
                {
                    // Finished countdown, remove projectile and create black hole
                    if (curTime >= (Float) projs.get(toCheck))
                    {
                        createBlackHole(toCheck, EXPLOSION_DURATION);
                        projs.remove(toCheck);
                    }
                }
                // Register projectile and immediately start countdown
                else
                {
                    projs.put(toCheck, curTime + EXPLOSION_DELAY);
                    nextRender = curTime;
                }
            }
        }
    }

    private void checkProjs()
    {
        // Scan for any projectiles that haven't been registered yet
        scanForProjs();

        // Show countdowns for registered projectiles
        if (curTime > nextRender)
        {
            nextRender = curTime + RENDER_TEXT_INTERVAL;
            renderProjs();
        }

        if (!projs.isEmpty())
        {
            DamagingProjectileAPI tmp;

            // Check if any registered projectiles caused damage this frame
            for (Iterator iter = projs.keySet().iterator(); iter.hasNext();)
            {
                tmp = (DamagingProjectileAPI) iter.next();
                // Projectile hit something, spawn the black hole
                if (tmp.didDamage())
                {
                    // Since it spawns on top of the target, limit the duration
                    createBlackHole(tmp, EXPLOSION_DURATION * EXPLOSION_HIT_MODIFIER);
                    iter.remove();
                }
            }
        }
    }

    private void createBlackHole(DamagingProjectileAPI shell, float duration)
    {
        Vector2f location = shell.getLocation();
        // Remove the projectile
        engine.removeEntity(shell);
        engine.spawnExplosion(location, NULLVEL,
                GRAVITY_WELL_PARTICLE_COLOR, 50f, duration);
        // Create a black hole in its place
        holes.put(new Vector2f(location), curTime + duration);
    }

    @Override
    public void advance(float amount, List events)
    {
        if (engine.isPaused())
        {
            return;
        }

        // Update time and number of frames since the beginning of combat
        curTime += amount;
        numUpdates++;

        // Do projectile checks
        checkProjs();

        // Do black hole checks
        if (!holes.isEmpty())
        {
            updateHoles(amount);
        }
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
    }
}
