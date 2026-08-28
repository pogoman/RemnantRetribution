package remret;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;

/**
 * Watches every battle the player is involved in, anywhere in the sector, and
 * converts destroyed Remnant hulls into retribution points. Every kill feeds the
 * persistent grudge, and nothing else - kills no longer inject an instant spike
 * into the crisis bar. The bar still tracks the grudge, but only through the
 * standing monthly creep in {@link RemRetActivityCause}; a single battle far from
 * the player's colonies raises the grudge without jolting the colony-crisis bar.
 */
public class RemRetKillTracker extends BaseCampaignEventListener {

	public RemRetKillTracker() {
		super(false);
	}

	@Override
	public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
		if (battle == null || !battle.isPlayerInvolved()) return;
		if (RemRetNetwork.isNeutralized()) return; // the war is over

		int points = 0;       // all kills -> long-term grudge
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
					// Nexus destruction feeds the grudge (biggest single source)
					// but deliberately does NOT slam the crisis bar - otherwise a
					// single Nexus kill could instantly roll/fire a retaliation.
					// The last Nexus is handled separately, via the death rattle.
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

		float mult = RemRetConfig.pointsMult();
		int grudgeAdd = Math.max(1, Math.round(points * mult));
		RemRetData.addGrudge(grudgeAdd);
		RemRetDebug.log("Destroyed Remnant forces: +" + grudgeAdd + " threat (grudge now "
				+ (int) RemRetData.getGrudge() + ").");

		// Kills feed only the grudge; the crisis bar is driven by the standing
		// monthly creep in RemRetActivityCause (grudge-scaled), never spiked
		// directly by a battle. This keeps clearing Remnants far from your
		// colonies from lurching the colony-crisis bar upward.

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

	/**
	 * Each destroyed Nexus yields a Gate Control Fragment in the battle salvage
	 * - cut from the station's command core, it can repair one dormant Gate
	 * (see {@link RemRetGateCMD}). Detection is done from the encounter's own
	 * battle snapshot rather than shared state, so this does not depend on
	 * callback ordering with {@link #reportBattleFinished}.
	 */
	@Override
	public void reportEncounterLootGenerated(FleetEncounterContextPlugin plugin, CargoAPI loot) {
		if (plugin == null || plugin.getBattle() == null) return;

		BattleAPI battle = plugin.getBattle();
		if (!battle.isPlayerInvolved()) return;

		int nexusKills = 0;
		for (CampaignFleetAPI other : battle.getNonPlayerSideSnapshot()) {
			if (other.getFaction() == null) continue;
			if (!Factions.REMNANTS.equals(other.getFaction().getId())) continue;

			boolean stationFleet = other.isStationMode();
			for (FleetMemberAPI loss : Misc.getSnapshotMembersLost(other)) {
				if (stationFleet || loss.isStation()) nexusKills++;
			}
		}
		if (nexusKills <= 0) return;

		if (RemRetConfig.gateFragments()) {
			loot.addSpecial(new SpecialItemData(RemRetGateCMD.ITEM_ID, null), nexusKills);
			RemRetDebug.log("Gate Control Fragment recovered from Nexus wreckage (x" + nexusKills + ").");
		}

		maybeDropPlanetkiller(loot);
	}

	/**
	 * The final Nexus was the network's vault: its wreckage yields a
	 * Domain-era planetkiller, held in stasis since the Collapse. One per
	 * campaign, only from the last Nexus, and never if the vanilla questline
	 * already produced the weapon.
	 */
	public static final String PK_DROPPED_KEY = "$remret_pkDropped";

	protected void maybeDropPlanetkiller(CargoAPI loot) {
		if (!RemRetConfig.pkDrop()) return;
		com.fs.starfarer.api.campaign.rules.MemoryAPI mem =
				com.fs.starfarer.api.Global.getSector().getMemoryWithoutUpdate();
		if (mem.getBoolean(PK_DROPPED_KEY)) return;
		if (mem.getBoolean("$pk_recovered") || mem.getBoolean("$pk_missionCompleted")) {
			return;
		}

		// a nexus died in this battle (caller checked); is the network now dark?
		RemRetNetwork.invalidateCensus();
		boolean lastNexusDown = RemRetNetwork.liveNexusCount() == 0
				|| RemRetNetwork.isPendingDeathRattle();
		if (!lastNexusDown) return;

		loot.addSpecial(new SpecialItemData(
				com.fs.starfarer.api.impl.campaign.ids.Items.PLANETKILLER, null), 1);
		com.fs.starfarer.api.Global.getSector().getMemoryWithoutUpdate()
				.set(PK_DROPPED_KEY, true);
		RemRetDebug.log("PLANETKILLER recovered from the final Nexus's vault.");

		com.fs.starfarer.api.impl.campaign.intel.MessageIntel msg =
				new com.fs.starfarer.api.impl.campaign.intel.MessageIntel(
						"Deep in the shattered Nexus's core, your salvage crews find a stasis "
						+ "vault older than the network itself - and within it, a Domain-era "
						+ "planetkiller. The machines were not hoarding it. They were "
						+ "guarding it.", Misc.getTextColor());
		com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(msg);
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
