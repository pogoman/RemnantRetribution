package remret;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.util.Misc;

/**
 * Debug/testing helpers, all opt-in via the Debug section of the settings menu.
 * Actions run once per game load from the mod plugin.
 */
public class RemRetDebug {

	public static final String GRUDGE_INJECTED_FLAG = "remret_debugGrudgeInjected";

	public static void log(String msg) {
		if (RemRetConfig.debugLogging()) {
			Global.getLogger(RemRetDebug.class).info("[RemRet] " + msg);
		}
	}

	public static void runOnGameLoad() {
		if (RemRetConfig.debugGrantColony()) {
			grantColonyIfNone();
		}
		injectStartingGrudge();
		if (RemRetConfig.debugForceStrike()) {
			forceStrike();
		}
	}

	/**
	 * Shoves the colony crisis bar to its maximum so the retaliation strike
	 * fires immediately (assuming the crisis has already rolled, or the Remnant
	 * factor wins the roll). Requires the crisis intel to already exist - i.e.
	 * you already own a colony. If you granted a colony this same load, load the
	 * save once more so the crisis exists first.
	 */
	private static void forceStrike() {
		HostileActivityEventIntel intel = HostileActivityEventIntel.get();
		if (intel == null) {
			Global.getLogger(RemRetDebug.class).info("[RemRet] debugForceStrike: no colony crisis active yet "
					+ "(need a colony first - reload once one exists).");
			return;
		}
		if (RemRetData.getGrudge() < RemRetConfig.minGrudgeForInvasion()) {
			RemRetData.setGrudge(RemRetConfig.minGrudgeForInvasion());
		}
		intel.setProgress(HostileActivityEventIntel.MAX_PROGRESS);
		Global.getLogger(RemRetDebug.class).info("[RemRet] debugForceStrike: pushed crisis bar to "
				+ HostileActivityEventIntel.MAX_PROGRESS + "; the rolled event fires on the next update.");
	}

	/**
	 * If the player owns no colony, hands them the nearest small NPC market so
	 * the vanilla hostile activity intel spins up and the crisis can be tested.
	 */
	private static void grantColonyIfNone() {
		if (!Misc.getPlayerMarkets(false).isEmpty()) return;

		SectorEntityToken playerFleet = Global.getSector().getPlayerFleet();
		if (playerFleet == null) return;

		MarketAPI best = null;
		float bestDist = Float.MAX_VALUE;
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			if (market.isPlayerOwned()) continue;
			if (market.getPrimaryEntity() == null) continue;
			if (market.isHidden()) continue;
			if (market.getSize() > 4) continue; // keep economic disruption small
			if (Factions.REMNANTS.equals(market.getFactionId())) continue;

			float dist = Misc.getDistance(playerFleet.getLocationInHyperspace(),
					market.getPrimaryEntity().getLocationInHyperspace());
			if (dist < bestDist) {
				bestDist = dist;
				best = market;
			}
		}

		if (best == null) {
			log("debugGrantColony: no suitable NPC market found to grant.");
			return;
		}

		String prevFaction = best.getFactionId();
		best.setFactionId(Factions.PLAYER);
		best.setPlayerOwned(true);

		Global.getLogger(RemRetDebug.class).info("[RemRet] debugGrantColony: granted "
				+ best.getName() + " (size " + best.getSize() + ", was " + prevFaction
				+ ") to the player for crisis testing.");
	}

	private static void injectStartingGrudge() {
		int amount = RemRetConfig.debugStartingGrudge();
		if (amount <= 0) return;

		boolean already = Global.getSector().getPersistentData().containsKey(GRUDGE_INJECTED_FLAG);
		if (already) return;
		if (RemRetData.getGrudge() > 0) return;

		RemRetData.setGrudge(amount);
		Global.getSector().getPersistentData().put(GRUDGE_INJECTED_FLAG, true);
		Global.getLogger(RemRetDebug.class).info("[RemRet] debugStartingGrudge: set grudge to " + amount + ".");
	}
}
