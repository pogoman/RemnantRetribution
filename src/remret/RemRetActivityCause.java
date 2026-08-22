package remret;

import java.awt.Color;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityCause2;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;

/**
 * The standing cause row: while the network holds a grudge, the event bar
 * creeps upward each month (representing the network actively planning), and
 * hunter-killer groups operate in the player's systems with strength scaled to
 * the accumulated grudge.
 */
public class RemRetActivityCause extends BaseHostileActivityCause2 {

	public RemRetActivityCause(HostileActivityEventIntel intel) {
		super(intel);
	}

	@Override
	public boolean shouldShow() {
		return getProgress() > 0;
	}

	@Override
	public int getProgress() {
		if (RemRetNetwork.isNeutralized()) return 0;
		float grudge = RemRetData.getGrudge();
		if (grudge <= 0) return 0;
		int progress = 1 + (int) (grudge / RemRetConfig.grudgePerMonthlyPoint());
		int cap = RemRetConfig.maxMonthlyProgress();
		if (progress > cap) progress = cap;
		// a fragmented network plans more slowly
		progress = Math.round(progress * RemRetNetwork.pressureMult());
		if (progress < 1) progress = 1;
		return progress;
	}

	@Override
	public String getDesc() {
		return "Threat assessment by the Remnant network";
	}

	@Override
	public float getMagnitudeContribution(StarSystemAPI system) {
		if (RemRetNetwork.isNeutralized()) return 0f;
		float grudge = RemRetData.getGrudge();
		// hunter-killer presence only begins once the network genuinely cares
		if (grudge < RemRetConfig.minGrudgeForHunters()) return 0f;

		float mag = grudge / RemRetConfig.grudgeForFullMagnitude();
		float max = RemRetConfig.maxMagnitude();
		if (mag > max) mag = max;

		// hunter presence fades as the command network fragments
		mag *= RemRetNetwork.pressureMult();

		mag = Math.round(mag * 100f) / 100f;
		return mag;
	}

	@Override
	public TooltipCreator getTooltip() {
		return new BaseFactorTooltip() {
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				float opad = 10f;
				Color h = Misc.getHighlightColor();

				tooltip.addPara("Your fleet's destruction of Remnant forces has not gone unnoticed. "
						+ "Surviving nodes of the Remnant network have assessed your polity as a "
						+ "threat to be eliminated, and hunter-killer groups have been dispatched "
						+ "to your space.", 0f);

				int frigates = RemRetData.getKills(RemRetData.KILL_FRIGATE);
				int destroyers = RemRetData.getKills(RemRetData.KILL_DESTROYER);
				int cruisers = RemRetData.getKills(RemRetData.KILL_CRUISER);
				int capitals = RemRetData.getKills(RemRetData.KILL_CAPITAL);
				int nexuses = RemRetData.getKills(RemRetData.KILL_NEXUS);

				tooltip.addPara("Remnant warships destroyed: %s frigates, %s destroyers, "
						+ "%s cruisers, %s capitals.", opad, h,
						"" + frigates, "" + destroyers, "" + cruisers, "" + capitals);

				if (nexuses > 0) {
					tooltip.addPara("Remnant Nexus stations destroyed: %s.", opad,
							Misc.getNegativeHighlightColor(), "" + nexuses);
				}

				tooltip.addPara("Current threat assessment: %s points. Destroying larger hulls "
						+ "raises it faster, and the destruction of a Nexus most of all.", opad,
						h, "" + (int) RemRetData.getGrudge());

				int live = RemRetNetwork.liveNexusCount();
				int baseline = RemRetNetwork.getBaseline();
				int destroyed = baseline - live;
				if (destroyed < 0) destroyed = 0;

				if (live > 0 && destroyed == 0) {
					tooltip.addPara("Network status: %s. The Remnant command network operates at "
							+ "full capability, coordinated through its Nexus stations.", opad,
							Misc.getNegativeHighlightColor(), "coherent");
				} else if (live > 0) {
					tooltip.addPara("Network status: %s - you have destroyed %s of %s known command "
							+ "nexuses. The network's reach diminishes with each loss, easing the "
							+ "pressure on your colonies.", opad, h,
							"fragmented", "" + destroyed, "" + baseline);
				} else if (RemRetConfig.canNeutralize()) {
					tooltip.addPara("Network status: %s - every known command nexus has been "
							+ "destroyed. Expect a final convergence of the network's surviving "
							+ "forces; defeat it, and the Remnant threat ends %s.", opad,
							Misc.getNegativeHighlightColor(), "decapitated", "permanently");
				} else {
					tooltip.addPara("Network status: %s - every known command nexus has been "
							+ "destroyed, yet dormant forces still answer the network's grudge.", opad,
							Misc.getNegativeHighlightColor(), "decapitated");
				}

				tooltip.addPara("Defeating the retaliation force, when it comes, will cause the "
						+ "network to %s.", opad,
						Misc.getPositiveHighlightColor(), "substantially lower its assessment");
			}
		};
	}
}
