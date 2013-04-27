package data.scripts.plugins;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.lwjgl.util.vector.Vector2f;

public class StarWarsShieldPlugin implements EveryFrameCombatPlugin
{
    // How long the shield will be down (compared to a normal overload)
    private static final float SHIELD_LOSS_DURATION_MODIFIER = 1.0f;
    // Ships with disabled shields (key = ship, value = time to restore shields)
    private final Map disabled = new HashMap();
    // The current combat engine instance
    private CombatEngineAPI engine;
    // How long the current combat has been going for
    private float elapsedTime = 0f;

    @Override
    public void advance(float amount, List events)
    {
        if (engine.isPaused())
        {
            return;
        }

        elapsedTime += amount;

        ShipAPI ship;
        if (!disabled.isEmpty())
        {
            // Check all ships with disabled shields to see if they should
            // be re-enabled yet
            Map.Entry tmp;
            for (Iterator iter = disabled.entrySet().iterator(); iter.hasNext();)
            {
                tmp = (Map.Entry) iter.next();
                // Time has elapsed, re-enable shields
                if (elapsedTime >= (Float) tmp.getValue())
                {
                    ship = (ShipAPI) tmp.getKey();
                    ship.getMutableStats().getShieldUnfoldRateMult().unmodify("sw_shieldplugin");
                    engine.addFloatingText(new Vector2f(ship.getLocation().x,
                            ship.getLocation().y - (ship.getCollisionRadius() * 1.1f)),
                            "Shields restored.", 30f, Color.GREEN, ship, 0f, 0f);
                    iter.remove();
                }
            }
        }

        float disabledTime;
        // Check all ships on the battlefield to see if they are overloaded
        for (Iterator iter = engine.getShips().iterator(); iter.hasNext();)
        {
            ship = (ShipAPI) iter.next();

            // Ship is overloaded; end overload and disable shields
            if (ship.getFluxTracker().isOverloaded())
            {
                disabledTime = SHIELD_LOSS_DURATION_MODIFIER
                        * ship.getFluxTracker().getOverloadTimeRemaining();
                disabled.put(ship, elapsedTime + disabledTime);
                ship.getFluxTracker().stopOverload();
                ship.getMutableStats().getShieldUnfoldRateMult().modifyPercent("sw_shieldplugin", -200f);
                engine.addFloatingText(new Vector2f(ship.getLocation().x,
                        ship.getLocation().y - (ship.getCollisionRadius() * 1.1f)),
                        "Shields disabled for " + (int) disabledTime + " seconds.",
                        30f, Color.YELLOW, ship, 0f, 0f);
            }
        }
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
        disabled.clear();
        elapsedTime = 0f;
    }
}
