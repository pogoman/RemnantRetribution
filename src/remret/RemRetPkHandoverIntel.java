package remret;

import java.awt.Color;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.HA_CMD;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * While the player carries the planetkiller, the Luddic Path reaches out
 * through intermediaries: hand over the God-cursed totem, and holy peace is
 * yours until the End of Days - the same permanent pather agreement the
 * vanilla questline grants, without the questline. Managed by
 * {@link RemRetPkHandoverScript}.
 */
public class RemRetPkHandoverIntel extends BaseIntelPlugin {

	public static final String BUTTON_HANDOVER = "remret_button_pk_handover";

	public static boolean playerHasPk() {
		return Global.getSector().getPlayerFleet().getCargo()
				.getQuantity(CargoItemType.SPECIAL,
						new SpecialItemData(Items.PLANETKILLER, null)) > 0;
	}

	@Override
	public String getName() {
		return "The Path Covets the Planetkiller";
	}

	@Override
	public String getIcon() {
		String crest = Global.getSector().getFaction(Factions.LUDDIC_PATH).getCrest();
		if (crest != null) return crest;
		return super.getIcon();
	}

	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
								   Color tc, float initPad) {
		info.addPara("Hand it over: permanent peace with the Luddic Path", initPad, tc,
				Misc.getHighlightColor(), "permanent peace");
	}

	@Override
	public boolean hasSmallDescription() {
		return true;
	}

	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		Color h = Misc.getHighlightColor();

		info.addPara("Word of the planetkiller in your hold has reached the Luddic Path. "
				+ "Through intermediaries - a shrine curate here, a shepherd's courier there - "
				+ "an understanding is quietly offered: surrender the God-cursed totem to the "
				+ "True Faithful, and your worlds will know holy peace until the End of Days.",
				opad);
		info.addPara("This is the same %s the Path grants in exchange for the weapon by other "
				+ "means: cells on your colonies stand down, permanently. The Path does not "
				+ "want to use it. A demonic thing feared is a relic; a relic held is power.",
				opad, h, "permanent agreement");
		info.addPara("Or keep it. It is, after all, only a weapon.", opad);

		if (playerHasPk()) {
			info.addButton("Surrender the planetkiller to the Path", BUTTON_HANDOVER,
					width - 2 * opad, 20f, opad * 2f);
		} else {
			info.addPara("The planetkiller is no longer in your fleet's cargo.", opad,
					Misc.getNegativeHighlightColor(), h, "");
		}
	}

	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (!BUTTON_HANDOVER.equals(buttonId)) {
			super.buttonPressConfirmed(buttonId, ui);
			return;
		}
		if (!playerHasPk()) {
			ui.updateUIForItem(this);
			return;
		}

		Global.getSector().getPlayerFleet().getCargo().removeItems(CargoItemType.SPECIAL,
				new SpecialItemData(Items.PLANETKILLER, null), 1);
		HA_CMD.setPatherAgreement(true, 0f); // duration <= 0: permanent

		com.fs.starfarer.api.impl.campaign.intel.MessageIntel msg =
				new com.fs.starfarer.api.impl.campaign.intel.MessageIntel(
						"The planetkiller passes into the keeping of the True Faithful. "
						+ "\"Holy peace be upon you, until the End of Days when all shall "
						+ "be judged.\" Luddic Path cells across your worlds stand down - "
						+ "permanently.", Misc.getTextColor());
		String crest = Global.getSector().getFaction(Factions.LUDDIC_PATH).getCrest();
		if (crest != null) msg.setIcon(crest);
		Global.getSector().getCampaignUI().addMessage(msg);

		RemRetDebug.log("Planetkiller handed to the Path: permanent pather agreement set.");
		endAfterDelay();
		ui.updateUIForItem(this);
	}

	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_STORY);
		tags.add(Factions.LUDDIC_PATH);
		return tags;
	}
}
