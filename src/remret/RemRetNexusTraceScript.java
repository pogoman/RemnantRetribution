package remret;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.RemnantHostileActivityFactor;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * The trace clock: while hunter-killers are active in the player's space,
 * their patrol vectors can be back-traced. Every
 * {@link RemRetConfig#nexusTraceDays()} days of active hunting, the surviving
 * Nexus nearest the player's colonies is revealed as a
 * {@link RemRetNexusIntel} map marker - the same play pattern as learning a
 * pirate base's location and going to burn it down.
 *
 * Entering a system that contains a live Nexus reveals it immediately (you
 * are looking right at it), without consuming the trace clock.
 *
 * Transient: re-added on every game load, never serialized. The trace clock
 * timestamp lives in persistent data; the revealed intel entries persist in
 * the save on their own.
 */
public class RemRetNexusTraceScript implements EveryFrameScript {

	protected IntervalUtil interval = new IntervalUtil(0.4f, 0.6f); // days

	@Override
	public void advance(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		interval.advance(days);
		if (!interval.intervalElapsed()) return;

		if (!RemRetConfig.nexusIntel()) return;
		if (RemRetNetwork.isNeutralized()) return;

		// free reveal: the player is in a system with a live Nexus
		revealCurrentLocation();

		// the timed trace only runs while there are hunters to trace
		if (RemRetData.getGrudge() < RemRetConfig.minGrudgeForHunters()) {
			// no active hunting: the clock does not run
			RemRetData.setNexusTraceTimestamp(Global.getSector().getClock().getTimestamp());
			return;
		}

		// one traced target at a time: while a revealed Nexus still stands, hold
		// the clock so the next is only traced after the current one is destroyed
		if (RemRetNexusIntel.hasOutstandingLive()) {
			RemRetData.setNexusTraceTimestamp(Global.getSector().getClock().getTimestamp());
			return;
		}

		long last = RemRetData.getNexusTraceTimestamp();
		if (last == 0) {
			// hunters just became active: start the clock
			RemRetData.setNexusTraceTimestamp(Global.getSector().getClock().getTimestamp());
			return;
		}

		float elapsed = Global.getSector().getClock().getElapsedDaysSince(last);
		if (elapsed < RemRetConfig.nexusTraceDays()) return;

		StarSystemAPI target = pickTraceTarget();
		if (target == null) return; // every surviving Nexus already traced

		reveal(target);
		RemRetData.setNexusTraceTimestamp(Global.getSector().getClock().getTimestamp());
	}

	protected void revealCurrentLocation() {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		if (player == null) return;
		LocationAPI loc = player.getContainingLocation();
		if (!(loc instanceof StarSystemAPI)) return;
		StarSystemAPI system = (StarSystemAPI) loc;
		if (RemRetNexusIntel.existsFor(system)) return;
		if (RemnantHostileActivityFactor.getRemnantNexus(system) == null) return;
		reveal(system);
	}

	/**
	 * The next Nexus the trace uncovers: the surviving, untraced one nearest to
	 * the player's colonies (hunter-killers operating in your space are traced
	 * back along their approach vectors) - or nearest the player fleet if no
	 * colonies exist.
	 */
	protected StarSystemAPI pickTraceTarget() {
		StarSystemAPI best = null;
		float bestDist = Float.MAX_VALUE;

		for (StarSystemAPI system : Global.getSector().getStarSystems()) {
			if (RemRetNexusIntel.existsFor(system)) continue;
			if (RemnantHostileActivityFactor.getRemnantNexus(system) == null) continue;

			float dist = distanceToPlayerSpace(system);
			if (dist < bestDist) {
				bestDist = dist;
				best = system;
			}
		}
		return best;
	}

	protected float distanceToPlayerSpace(StarSystemAPI system) {
		float best = Float.MAX_VALUE;
		for (MarketAPI market : Misc.getPlayerMarkets(false)) {
			if (market.getStarSystem() == null) continue;
			float dist = Misc.getDistanceLY(system.getLocation(),
					market.getStarSystem().getLocation());
			if (dist < best) best = dist;
		}
		if (best < Float.MAX_VALUE) return best;

		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		if (player != null) {
			return Misc.getDistanceLY(system.getLocation(), player.getLocationInHyperspace());
		}
		return best;
	}

	protected void reveal(StarSystemAPI system) {
		RemRetNexusIntel intel = new RemRetNexusIntel(system);
		Global.getSector().getIntelManager().addIntel(intel);
		RemRetDebug.log("Nexus traced: " + system.getBaseName() + ".");
	}

	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}
}
