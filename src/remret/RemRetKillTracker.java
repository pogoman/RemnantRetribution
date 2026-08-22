package remret;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.util.Misc;

/**
 * Watches every battle the player is involved in, anywhere in the sector, and
 * converts destroyed Remnant hulls into retribution points. Points are always
 * accumulated in persistent data; if the colony crisis intel is active, they
 * are also injected into the event bar as a one-time factor so the player sees
 * "Remnant forces destroyed: +N" after each battle.
 */
public class RemRetKillTracker extends BaseCampaignEventListener {

	public RemRetKillTracker() {
		super(false);
	}

	@Override
	public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
		if (battle == null || !battle.isPlayerInvolved()) return;
		if (RemRetNetwork.isNeutralized()) return; // the war is over

		int points = 0;
		boolean nexusKilled = false;
		for (CampaignFleetAPI other : battle.getNonPlayerSideSnapshot()) {
			if (other.getFaction() == null) continue;
			if (!Factions.REMNANTS.equals(other.getFaction().getId())) continue;
			// hunter-killers this mod sends at the player don't feed the grudge -
			// the network already wrote them off when it dispatched them
			if (other.getMemoryWithoutUpdate().getBoolean(RemRetHostileActivityFactor.HUNTER_FLEET_FLAG)) continue;
			// likewise the retaliation strike and reconnaissance fleets: defeating
			// the crisis is rewarded by the grudge halving, never punished
			if (isFleetFromOwnEvent(other)) continue;

			boolean stationFleet = other.isStationMode();
			for (FleetMemberAPI loss : Misc.getSnapshotMembersLost(other)) {
				if (stationFleet || loss.isStation()) {
					points += RemRetConfig.pointsNexus();
					RemRetData.addKill(RemRetData.KILL_NEXUS);
					nexusKilled = true;
					if (other.getContainingLocation() != null) {
						RemRetNetwork.setLastNexusSystemId(other.getContainingLocation().getId());
					}
					continue;
				}
				HullSize size = loss.getHullSpec().getHullSize();
				if (size == HullSize.FRIGATE) {
					points += RemRetConfig.pointsFrigate();
					RemRetData.addKill(RemRetData.KILL_FRIGATE);
				} else if (size == HullSize.DESTROYER) {
					points += RemRetConfig.pointsDestroyer();
					RemRetData.addKill(RemRetData.KILL_DESTROYER);
				} else if (size == HullSize.CRUISER) {
					points += RemRetConfig.pointsCruiser();
					RemRetData.addKill(RemRetData.KILL_CRUISER);
				} else if (size == HullSize.CAPITAL_SHIP) {
					points += RemRetConfig.pointsCapital();
					RemRetData.addKill(RemRetData.KILL_CAPITAL);
				}
			}
		}

		if (points <= 0) return;

		points = Math.round(points * RemRetConfig.pointsMult());
		if (points < 1) points = 1;

		RemRetData.addGrudge(points);
		RemRetDebug.log("Destroyed Remnant forces: +" + points + " threat (grudge now "
				+ (int) RemRetData.getGrudge() + ").");

		HostileActivityEventIntel intel = HostileActivityEventIntel.get();
		if (intel != null && !intel.isEnded() && !intel.isEnding()) {
			intel.addFactor(new RemRetKillsOneTimeFactor(points));
		}

		if (nexusKilled) {
			RemRetNetwork.invalidateCensus();
			// killed the last one? the shattered network converges for its
			// final strike (launched by the factor, which owns strike logic)
			if (RemRetConfig.canNeutralize() && RemRetNetwork.liveNexusCount() == 0) {
				RemRetNetwork.setPendingDeathRattle(true);
				RemRetDebug.log("Last Remnant Nexus destroyed - final convergence pending.");
			}
		}
	}

	/** True if this fleet belongs to the mod's own active strike or probe fleet-group. */
	protected boolean isFleetFromOwnEvent(CampaignFleetAPI fleet) {
		return fleetBelongsTo(fleet, RemRetHostileActivityFactor.INVASION_KEY)
				|| fleetBelongsTo(fleet, RemRetHostileActivityFactor.PROBE_KEY);
	}

	protected boolean fleetBelongsTo(CampaignFleetAPI fleet, String memoryKey) {
		Object curr = com.fs.starfarer.api.Global.getSector().getMemoryWithoutUpdate().get(memoryKey);
		if (curr instanceof com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel) {
			return ((com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel) curr).getFleets().contains(fleet);
		}
		return false;
	}

}
