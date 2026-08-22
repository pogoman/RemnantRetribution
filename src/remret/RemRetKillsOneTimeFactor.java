package remret;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;

/**
 * One-time progress injection after a battle in which the player destroyed
 * Remnant forces. Mirrors how vanilla applies HAShipsDestroyedFactor, but with
 * positive points.
 */
public class RemRetKillsOneTimeFactor extends BaseOneTimeFactor {

	public RemRetKillsOneTimeFactor(int points) {
		super(points);
	}

	@Override
	public String getDesc(BaseEventIntel intel) {
		return "Remnant forces destroyed";
	}

	@Override
	public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
		return new BaseFactorTooltip() {
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				tooltip.addPara("The destruction of Remnant forces at your fleet's hands has been "
						+ "registered by the surviving nodes of the Remnant network. Larger hulls "
						+ "raise its threat assessment of your polity faster, and the destruction "
						+ "of a Nexus most of all.", 0f);
			}
		};
	}
}
