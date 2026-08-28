package remret;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;

/**
 * The retaliation strike as a raid fleet-group whose fleets can actually carry
 * out the saturation bombardment on arrival, instead of relying on the
 * operation timing out (autoresolve) to deliver it abstractly.
 *
 * Vanilla gates a fleet's live bombardment behind carrying enough fuel to pay
 * the cost (fleet.maxFuel * 0.5 >= cost). Remnant war fleets rarely carry that
 * much, so the strike would sit in orbit doing nothing until its ~57-day timer
 * expired. Granting each fleet member a large FLEET_BOMBARD_COST_REDUCTION
 * zeroes that cost, so the armada bombards as soon as it reaches the colony -
 * a live event the player can intercept and fight.
 */
public class RemRetStrikeFGI extends GenericRaidFGI {

	public RemRetStrikeFGI(GenericRaidParams params) {
		super(params);
	}

	@Override
	protected void configureFleet(int size, CampaignFleetAPI fleet) {
		super.configureFleet(size, fleet);
		if (fleet == null) return;
		// summed per member (Misc.getFleetwideTotalMod), so this vastly exceeds
		// any colony's bombardment cost
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			member.getStats().getDynamic().getMod(Stats.FLEET_BOMBARD_COST_REDUCTION)
					.modifyFlat("remret_strike", 100000f);
		}
	}
}
