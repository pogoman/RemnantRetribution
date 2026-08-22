package remret;

import java.awt.Color;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.events.RemnantHostileActivityFactor;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * A traced Remnant Nexus: one intel entry per known surviving Nexus, with a
 * map marker on its star system - the retribution counterpart to hunting down
 * a pirate base. Destroying the station fragments the command network (easing
 * hunter-killer pressure), and destroying every Nexus opens the path to
 * permanent neutralization.
 *
 * Created by {@link RemRetNexusTraceScript}; ends (with an update ping) when
 * the Nexus in its system no longer exists.
 */
public class RemRetNexusIntel extends BaseIntelPlugin {

	public static final Object UPDATE_DESTROYED = new Object();

	protected StarSystemAPI system;
	protected boolean destroyed = false;

	protected IntervalUtil checkInterval = new IntervalUtil(0.8f, 1.2f); // days

	public RemRetNexusIntel(StarSystemAPI system) {
		this.system = system;
	}

	public StarSystemAPI getSystem() {
		return system;
	}

	/** Whether an intel entry (live or ending) already exists for this system. */
	public static boolean existsFor(StarSystemAPI system) {
		for (Object curr : Global.getSector().getIntelManager().getIntel(RemRetNexusIntel.class)) {
			if (((RemRetNexusIntel) curr).getSystem() == system) return true;
		}
		return false;
	}

	@Override
	protected void advanceImpl(float amount) {
		if (isEnding() || isEnded() || destroyed) return;

		float days = Global.getSector().getClock().convertToDays(amount);
		checkInterval.advance(days);
		if (!checkInterval.intervalElapsed()) return;

		if (RemnantHostileActivityFactor.getRemnantNexus(system) == null) {
			destroyed = true;
			sendUpdateIfPlayerHasIntel(UPDATE_DESTROYED, false);
			endAfterDelay();
		}
	}

	@Override
	protected String getName() {
		if (destroyed) {
			return "Remnant Nexus Destroyed - " + system.getBaseName();
		}
		return "Remnant Nexus - " + system.getBaseName();
	}

	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = getTitleColor(mode);
		Color tc = getBulletColorForMode(mode);
		Color h = Misc.getHighlightColor();

		info.addPara(getName(), c, 0f);

		bullet(info);
		if (getListInfoParam() == UPDATE_DESTROYED || destroyed) {
			info.addPara("The network's reach diminishes", tc, 3f);
		} else {
			info.addPara("Location: %s", 3f, tc, h, system.getBaseName());
		}
		unindent(info);
	}

	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		Color h = Misc.getHighlightColor();
		Color bad = Misc.getNegativeHighlightColor();
		Color good = Misc.getPositiveHighlightColor();

		info.addImage(getFactionForUIColors().getLogo(), width, 128, opad);

		if (destroyed) {
			info.addPara("The Remnant Nexus in the " + system.getNameWithLowercaseType()
					+ " has been destroyed.", opad);
		} else {
			info.addPara("Analysis of hunter-killer patrol vectors and drone transponder "
					+ "fragments places a Remnant command Nexus in the "
					+ system.getNameWithLowercaseType() + ". It is one of the nodes "
					+ "coordinating the network's campaign against your polity.", opad,
					h, system.getBaseName());

			info.addPara("Destroying it will fragment the command network, reducing "
					+ "hunter-killer presence in your space and the strength of any "
					+ "retaliation to come.", opad, good, "fragment the command network");
		}

		int live = RemRetNetwork.liveNexusCount();
		int baseline = RemRetNetwork.getBaseline();
		int dead = Math.max(0, baseline - live);

		info.addPara("Known command nexuses destroyed: %s of %s.", opad, h,
				"" + dead, "" + baseline);

		if (!destroyed && RemRetConfig.canNeutralize()) {
			if (live <= 1) {
				info.addPara("This is the last known Nexus. Its destruction will trigger "
						+ "a final convergence of the network's surviving forces - defeat "
						+ "that, and the Remnant threat to your polity ends permanently.",
						opad, bad, "final convergence");
			} else {
				info.addPara("Destroy every Nexus and the shattered network will converge "
						+ "for one final strike; defeating it ends the threat permanently.",
						opad, h, "permanently");
			}
		}

		addDeleteButton(info, width);
	}

	@Override
	public String getIcon() {
		return Global.getSector().getFaction(Factions.REMNANTS).getCrest();
	}

	@Override
	public com.fs.starfarer.api.campaign.FactionAPI getFactionForUIColors() {
		return Global.getSector().getFaction(Factions.REMNANTS);
	}

	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		CampaignFleetAPI nexus = RemnantHostileActivityFactor.getRemnantNexus(system);
		if (nexus != null) return nexus;
		return system.getCenter();
	}

	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_MILITARY);
		tags.add(Factions.REMNANTS);
		return tags;
	}

	@Override
	public String getSortString() {
		return "Remnant Nexus";
	}

	@Override
	public String getCommMessageSound() {
		return "ui_discovered_entity";
	}
}
