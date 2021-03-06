/*
 * Spirit, a study/biosample management tool for research.
 * Copyright (C) 2018 Idorsia Pharmaceuticals Ltd., Hegenheimermattweg 91,
 * CH-4123 Allschwil, Switzerland.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * @author Joel Freyss
 */

package com.actelion.research.spiritapp.ui.biosample;

import java.awt.Desktop;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;

import com.actelion.research.spiritapp.Spirit;
import com.actelion.research.spiritapp.ui.SpiritFrame;
import com.actelion.research.spiritapp.ui.admin.AdminActions;
import com.actelion.research.spiritapp.ui.audit.BiosampleHistoryDlg;
import com.actelion.research.spiritapp.ui.biosample.dialog.BiosampleDiscardDlg;
import com.actelion.research.spiritapp.ui.biosample.dialog.BiosampleDuplicatesDlg;
import com.actelion.research.spiritapp.ui.biosample.dialog.SetBiosampleQualityDlg;
import com.actelion.research.spiritapp.ui.biosample.dialog.SetBiosampleStatusDlg;
import com.actelion.research.spiritapp.ui.biosample.dialog.SetExpiryDateDlg;
import com.actelion.research.spiritapp.ui.biosample.dialog.UpdateAmountDlg;
import com.actelion.research.spiritapp.ui.biosample.edit.CreateChildrenDlg;
import com.actelion.research.spiritapp.ui.biosample.edit.EditBiosampleDlg;
import com.actelion.research.spiritapp.ui.biosample.form.BiosampleFormDlg;
import com.actelion.research.spiritapp.ui.biosample.selector.SelectorDlg;
import com.actelion.research.spiritapp.ui.location.CheckinDlg;
import com.actelion.research.spiritapp.ui.location.CheckoutDlg;
import com.actelion.research.spiritapp.ui.pivot.PivotTable;
import com.actelion.research.spiritapp.ui.print.PrintingDlg;
import com.actelion.research.spiritapp.ui.result.edit.EditResultDlg;
import com.actelion.research.spiritapp.ui.util.SpiritChangeListener;
import com.actelion.research.spiritapp.ui.util.SpiritChangeType;
import com.actelion.research.spiritapp.ui.util.component.UserIdComboBox;
import com.actelion.research.spiritapp.ui.util.icons.ImageFactory;
import com.actelion.research.spiritcore.adapter.DBAdapter;
import com.actelion.research.spiritcore.business.Quality;
import com.actelion.research.spiritcore.business.biosample.Biosample;
import com.actelion.research.spiritcore.business.biosample.Biotype;
import com.actelion.research.spiritcore.business.biosample.Status;
import com.actelion.research.spiritcore.business.location.Location;
import com.actelion.research.spiritcore.business.result.Result;
import com.actelion.research.spiritcore.business.study.Study;
import com.actelion.research.spiritcore.services.SpiritRights;
import com.actelion.research.spiritcore.services.SpiritUser;
import com.actelion.research.spiritcore.services.dao.DAOBiosample;
import com.actelion.research.spiritcore.services.dao.DAOBiotype;
import com.actelion.research.spiritcore.services.dao.DAOSpiritUser;
import com.actelion.research.spiritcore.services.dao.JPAUtil;
import com.actelion.research.spiritcore.util.ListHashMap;
import com.actelion.research.spiritcore.util.MiscUtils;
import com.actelion.research.util.ui.FastFont;
import com.actelion.research.util.ui.JCustomLabel;
import com.actelion.research.util.ui.JExceptionDialog;
import com.actelion.research.util.ui.PopupAdapter;
import com.actelion.research.util.ui.UIUtils;
import com.actelion.research.util.ui.iconbutton.IconType;

public class BiosampleActions {

	public static class Action_New extends AbstractAction {

		private Biotype biotype;

		public Action_New(Biotype biotype) {
			super("New "+biotype.getName());
			this.biotype = biotype;
			putValue(AbstractAction.SMALL_ICON, new ImageIcon(ImageFactory.getImage(biotype, FastFont.getAdaptedSize(24))));
			setEnabled(SpiritRights.canEdit(new Biosample(), Spirit.getUser()));
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			Biosample b = new Biosample(biotype);
			new BiosampleFormDlg(b);
		}
	}

	public static class Action_NewChild extends AbstractAction {

		private Biosample parent;

		public Action_NewChild(Biosample parent) {
			super("Create Child");
			putValue(AbstractAction.SMALL_ICON, IconType.NEW.getIcon());
			setParent(parent);
		}

		public void setParent(Biosample parent) {
			this.parent = parent;
			setEnabled(parent!=null);
			setEnabled(SpiritRights.canEdit(new Biosample(), Spirit.getUser()));
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			Biosample b = new Biosample(parent==null || parent.getBiotype()==null || parent.getBiotype().getChildren().isEmpty()? null: parent.getBiotype().getChildren().iterator().next());
			b.setParent(parent);
			new BiosampleFormDlg(b);
		}
	}


	public static class Action_NewBatch extends AbstractAction {

		public Action_NewBatch() {
			super("New Biosamples");
			putValue(AbstractAction.MNEMONIC_KEY, (int)('a'));
			putValue(Action.SMALL_ICON, IconType.BIOSAMPLE.getIcon());
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				String biotype = Spirit.getConfig().getProperty("biosample.type", "");
				Biosample biosample = new Biosample();
				biosample.setBiotype(DAOBiotype.getBiotype(biotype));
				biosample.setInheritedStudy(SpiritFrame.getStudy());
				EditBiosampleDlg dlg = EditBiosampleDlg.createDialogForEditInTransactionMode(Collections.singletonList(biosample));
				dlg.setParticipatingStudy(SpiritFrame.getStudy());
				dlg.setVisible(true);
			} catch (Exception ex) {
				JExceptionDialog.showError(ex);
			}
		}
	}

	public static class Action_Duplicate extends AbstractAction {

		private List<Biosample> biosamples;

		public Action_Duplicate(List<Biosample> biosamples) {
			super("Duplicate");
			this.biosamples = biosamples;
			putValue(AbstractAction.MNEMONIC_KEY, (int)('d'));
			putValue(Action.SMALL_ICON, IconType.DUPLICATE.getIcon());
			setEnabled(SpiritRights.canReadBiosamples(biosamples, SpiritFrame.getUser()));
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				List<Biosample> res = new ArrayList<>();
				Map<Biosample, Biosample> old2copy = new HashMap<>();
				for (Biosample b : biosamples) {
					Biosample copy = b.clone();
					copy.setId(0);
					copy.setLocation(null);
					copy.setSampleId(null);
					copy.setContainerId(null);
					res.add(copy);
					old2copy.put(b, copy);
				}
				for (Biosample b : res) {
					if(b.getParent()!=null && old2copy.containsKey(b.getParent())) {
						b.setParent(old2copy.get(b.getParent()));
					}
				}

				EditBiosampleDlg.createDialogForEditInTransactionMode(res).setVisible(true);
			} catch (Exception ex) {
				JExceptionDialog.showError(ex);
			}

		}
	}

	public static class Action_Order extends AbstractAction {
		private final List<Biosample> biosamples;

		public Action_Order(List<Biosample> biosamples) {
			super("Order Biosamples from " + (DBAdapter.getInstance().getAutomatedStoreLocation().size()==1? DBAdapter.getInstance().getAutomatedStoreLocation().iterator().next().getName():" Automatic Stores"));
			this.biosamples = biosamples;
			putValue(Action.MNEMONIC_KEY, (int)('o'));
			boolean enabled = biosamples.size()>0;
			if(enabled) {
				for(Biosample b: biosamples) {
					if(b.getLocation()==null || !DBAdapter.getInstance().isInAutomatedStore(b.getLocation())) {
						enabled = false;
						break;
					}
				}
			}
			setEnabled(enabled);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			assert DBAdapter.getInstance().getAutomaticStores()!=null;
			ListHashMap<Location, Biosample> map = new ListHashMap<Location, Biosample>();
			for (Biosample b : biosamples) {
				map.add(b.getLocation(), b);
			}

			for (Location l: DBAdapter.getInstance().getAutomaticStores().keySet()) {
				if(map.get(l)==null || map.get(l).size()==0) continue;

				URL url = DBAdapter.getInstance().getAutomaticStores().get(l);
				assert url!=null;


				try {
					String cids = MiscUtils.flatten(Biosample.getContainerIds(biosamples), " ");
					String path = url + "?q=" + URLEncoder.encode(cids, "UTF-8");

					Desktop.getDesktop().browse(new URI(path));
				} catch(Exception ex) {
					JExceptionDialog.showError(ex);
				}


			}
		}
	}


	/**
	 * Print Labels of biosamples
	 */
	public static class Action_Print extends AbstractAction {
		private final List<Biosample> biosamples;

		public Action_Print(List<Biosample> biosamples) {
			super("Print Labels");
			this.biosamples = biosamples;
			putValue(Action.MNEMONIC_KEY, (int)('p'));
			putValue(Action.SMALL_ICON, IconType.PRINT.getIcon());
			putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control P"));
			if(biosamples==null) {
				setEnabled(true);
			} else {
				for(Biotype biotype: Biosample.getBiotypes(biosamples)) {
					if(biotype.isAbstract()) setEnabled(false);
				}
			}
		}

		public List<Biosample> getBiosamples() {
			return biosamples;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			if(getBiosamples()==null || getBiosamples().size()==0) return;
			try {
				new PrintingDlg(getBiosamples());
			} catch (Exception ex) {
				JExceptionDialog.showError(ex);
			}

		}
	}

	/**
	 * Selects samples based on a discriminator (ex: samples with different fluorophores)
	 */
	public static class Action_SelectWithDiscriminator extends AbstractAction {
		private final List<Biosample> biosamples;

		public Action_SelectWithDiscriminator(List<Biosample> biosamples) {
			super("Help me select some biosamples");
			this.biosamples = biosamples;
			putValue(Action.MNEMONIC_KEY, (int)('h'));
			putValue(Action.SMALL_ICON, IconType.BIOSAMPLE.getIcon());
			setEnabled(biosamples==null || (biosamples.size()>1 && Biosample.getBiotype(biosamples)!=null));
		}
		public List<Biosample> getBiosamples() {
			return biosamples;
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			new SelectorDlg(getBiosamples());
		}
	}

	/**
	 * Delete samples
	 */
	public static class Action_Delete extends AbstractAction {
		private final List<Biosample> biosamples;
		public Action_Delete(List<Biosample> biosamples) {
			super("Delete Batch");
			this.biosamples = biosamples;
			putValue(AbstractAction.MNEMONIC_KEY, (int)('d'));
			putValue(Action.SMALL_ICON, IconType.DELETE.getIcon());

			boolean enabled = biosamples.size()>0;
			for (Biosample biosample : biosamples) {
				if(!SpiritRights.canDelete(biosample, SpiritFrame.getUser())) {enabled = false; break;}
			}
			setEnabled(enabled);
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				BiosampleDiscardDlg.createDialogForDelete(biosamples);
			} catch (Exception ex) {
				JExceptionDialog.showError(ex);
			}
		}
	}

	/**
	 * Edit samples
	 */
	public static class Action_BatchEdit extends AbstractAction {

		private final List<Biosample> biosamples;
		private final Study participatingStudy;

		/**
		 * Constructor for an edit action (generic, you must implement getBiosamples)
		 */
		public Action_BatchEdit() {
			super("Edit");
			putValue(AbstractAction.MNEMONIC_KEY, (int)('e'));
			putValue(Action.SMALL_ICON, IconType.EDIT.getIcon());
			this.biosamples = null;
			this.participatingStudy = null;
		}

		public Action_BatchEdit(String title, Study participatingStudy, boolean enabled) {
			super(title);
			putValue(AbstractAction.MNEMONIC_KEY, (int)('e'));
			putValue(Action.SMALL_ICON, IconType.BIOSAMPLE.getIcon());
			setEnabled(enabled);
			this.biosamples = null;
			this.participatingStudy = participatingStudy;
		}

		/**
		 * Constructor for an edit action (on the given biosamples)
		 * @param biosamples
		 */
		public Action_BatchEdit(List<Biosample> biosamples) {
			super(biosamples.size()==1?  "Edit " + (SpiritRights.canRead(biosamples.get(0), SpiritFrame.getUser())? biosamples.get(0).getSampleIdName(): null): "Edit All");
			this.biosamples = biosamples;
			putValue(AbstractAction.MNEMONIC_KEY, (int)('e'));
			putValue(Action.SMALL_ICON, IconType.EDIT.getIcon());
			setEnabled(SpiritRights.canEditBiosamples(biosamples,SpiritFrame.getUser()));
			this.participatingStudy = null;
		}

		public List<Biosample> getBiosamples() {
			return biosamples;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				//				JPAUtil.pushEditableContext(Spirit.getUser());
				List<Biosample> biosamples = getBiosamples();
				if(biosamples==null || biosamples.size()==0) throw new Exception("There are no samples to edit");

				//Analyze the data, to check if we can edit
				if(!SpiritRights.canEditBiosamples(biosamples, Spirit.askForAuthentication())) {
					throw new Exception("You are not allowed to edit those biosamples");
				}

				if(participatingStudy==null && biosamples.size()==1 && biosamples.get(0).getInheritedStudy()==null) {
					//We open the component editor only if we have one item and no study
					new BiosampleFormDlg(biosamples.get(0));
				} else {
					//Open the batch edit dialog
					EditBiosampleDlg dlg = EditBiosampleDlg.createDialogForEditInTransactionMode(biosamples);
					dlg.setParticipatingStudy(participatingStudy!=null? participatingStudy: SpiritFrame.getStudy());
					dlg.setVisible(true);
				}

			} catch(Exception ex) {
				JExceptionDialog.showError(ex);
			}
		}
	}

	public static class Action_NewChildren extends AbstractAction {
		private final List<Biosample> biosamples;

		public Action_NewChildren(List<Biosample> biosamples) {
			super("Add Children");
			this.biosamples = biosamples;
			putValue(AbstractAction.MNEMONIC_KEY, (int)('a'));
			putValue(AbstractAction.SMALL_ICON, IconType.BIOSAMPLE.getIcon());

			boolean canEdit = SpiritFrame.getUser()!=null;
			for (Biosample b : biosamples) {
				canEdit = canEdit && SpiritRights.canEdit(b, SpiritFrame.getUser()) && b.getBiotype()!=null;
			}
			setEnabled(canEdit);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				CreateChildrenDlg dlg = new CreateChildrenDlg(biosamples, null);
				dlg.setVisible(true);
				List<Biosample> children = dlg.getChildren();
				if(children!=null) {
					EditBiosampleDlg dlg2 = EditBiosampleDlg.createDialogForEditInTransactionMode(children);
					dlg2.setVisible(true);
				}
			} catch (Exception ex) {
				JExceptionDialog.showError(ex);
			}
		}
	}

	public static class Action_NewResults extends AbstractAction {
		private final List<Biosample> biosamples;

		public Action_NewResults(List<Biosample> biosamples) {
			super("Add Results");
			this.biosamples = biosamples;
			putValue(AbstractAction.MNEMONIC_KEY, (int)('a'));
			putValue(AbstractAction.SMALL_ICON, IconType.RESULT.getIcon());

			boolean canEdit = SpiritFrame.getUser()!=null;
			for (Biosample b : biosamples) {
				canEdit = canEdit && SpiritRights.canEdit(b, SpiritFrame.getUser()) && b.getBiotype()!=null;
			}
			setEnabled(canEdit);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				List<Result> results = new ArrayList<>();
				for (Biosample biosample : biosamples) {
					Result r = new Result();
					r.setBiosample(biosample);
					results.add(r);
				}
				new EditResultDlg(true, results);
			} catch (Exception ex) {
				JExceptionDialog.showError(ex);
			}
		}
	}

	public static class Action_Amount extends AbstractAction {
		private final List<Biosample> biosamples;
		public Action_Amount(List<Biosample> biosamples) {
			super("Update Amount");
			this.biosamples = biosamples;
			putValue(AbstractAction.MNEMONIC_KEY, (int)('c'));

			boolean canEdit = true;
			for (Biosample b : biosamples) {
				canEdit = canEdit && SpiritRights.canEdit(b, SpiritFrame.getUser()) && b.getBiotype()!=null && !b.isAbstract() && b.getBiotype().getAmountUnit()!=null;
			}
			setEnabled(canEdit);
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				new UpdateAmountDlg(biosamples);
			} catch (Exception ex) {
				JExceptionDialog.showError(ex);
			}
		}
	}

	public static class Action_History extends AbstractAction {
		private final Collection<Biosample> biosamples;
		public Action_History(Collection<Biosample> biosamples) {
			super("Audit Trail");
			this.biosamples = biosamples;
			putValue(AbstractAction.MNEMONIC_KEY, (int)('a'));
			putValue(Action.SMALL_ICON, IconType.HISTORY.getIcon());
			setEnabled(biosamples.size()==1);
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				new BiosampleHistoryDlg(biosamples.iterator().next());
			} catch(Exception ex) {
				JExceptionDialog.showError(ex);
			}
		}
	}

	public static class Action_SetQuality extends AbstractAction {
		private final List<Biosample> biosamples;
		private Quality quality;
		public Action_SetQuality(List<Biosample> biosamples, Quality quality) {
			super(quality.getName() + (quality==Quality.VALID?" (default)":"" ));
			this.biosamples = biosamples;
			this.quality = quality;
			putValue(AbstractAction.MNEMONIC_KEY, (int)(quality.getName().charAt(0)));
			for (Biosample b : biosamples) {
				if(!SpiritRights.canEdit(b, SpiritFrame.getUser())) setEnabled(false);
			}

		}
		@Override
		public void actionPerformed(ActionEvent e) {
			new SetBiosampleQualityDlg(biosamples, quality);
		}
	}

	public static class Action_SetStatus extends AbstractAction {
		private final List<Biosample> biosamples;
		private Status status;

		public Action_SetStatus(List<Biosample> biosamples, Status status) {
			super(status==null? "No Status": status.getName());
			this.biosamples = biosamples;
			this.status = status;
			for (Biosample b : biosamples) {
				if(b.isAbstract() || !SpiritRights.canEdit(b, SpiritFrame.getUser())) setEnabled(false);
			}

			if(status==Status.TRASHED) {
				putValue(AbstractAction.SMALL_ICON, IconType.TRASH.getIcon());
			}

		}
		@Override
		public void actionPerformed(ActionEvent e) {
			new SetBiosampleStatusDlg(biosamples, status);
		}
	}

	public static class Action_SetExpiryDate extends AbstractAction {
		private final List<Biosample> biosamples;

		public Action_SetExpiryDate(List<Biosample> biosamples) {
			super("Set Expiry Date");
			this.biosamples = biosamples;
			for (Biosample b : biosamples) {
				if(b.isAbstract() || !SpiritRights.canEdit(b, SpiritFrame.getUser())) setEnabled(false);
			}

			putValue(AbstractAction.SMALL_ICON, IconType.SANDGLASS.getIcon());

		}
		@Override
		public void actionPerformed(ActionEvent e) {
			new SetExpiryDateDlg(biosamples);
		}
	}



	public static class Action_AssignTo extends AbstractAction {
		private List<Biosample> biosamples;
		public Action_AssignTo(List<Biosample> biosamples) {
			super("Change Ownership (CreatedBy)");
			this.biosamples = biosamples;

			putValue(AbstractAction.SMALL_ICON, IconType.ADMIN.getIcon());
			putValue(AbstractAction.MNEMONIC_KEY, (int)'o');
			if(biosamples!=null) {
				boolean enabled = biosamples.size()>0;
				for (Biosample biosample : biosamples) {
					if(!SpiritRights.canDelete(biosample, SpiritFrame.getUser())) {enabled = false; break;}
				}
				setEnabled(enabled);
			}
		}

		public List<Biosample> getBiosamples() {
			return biosamples;
		}
		@Override
		public void actionPerformed(ActionEvent ev) {
			if(getBiosamples()==null || getBiosamples().size()==0) return;

			UserIdComboBox userIdComboBox = new UserIdComboBox();
			int res = JOptionPane.showOptionDialog(UIUtils.getMainFrame(),
					UIUtils.createVerticalBox(
							new JLabel("To whom would you like to assign those " + getBiosamples().size() + " samples?"),
							UIUtils.createHorizontalBox(userIdComboBox, Box.createHorizontalGlue())),
					"Change ownership",
					JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE,
					null,
					null,
					null);
			if(res!=JOptionPane.YES_OPTION) return;
			try {
				JPAUtil.pushEditableContext(SpiritFrame.getUser());
				String name = userIdComboBox.getText();
				if(name.length()==0) return;
				List<Biosample> biosamples = JPAUtil.reattach(getBiosamples());
				if(!SpiritRights.canEditBiosamples(biosamples, SpiritFrame.getUser())) throw new Exception("You are not allowed to edit those biosamples");
				SpiritUser admin = Spirit.askForAuthentication();
				SpiritUser u = DAOSpiritUser.loadUser(name);
				if(u==null) throw new Exception(name + " is an invalid user");
				res = JOptionPane.showConfirmDialog(null, "Are you sure to update the owner to " + u.getUsername()+" and the department to "+(u.getMainGroup()==null?"NA":u.getMainGroup())+"?", "Change Ownership", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				if(res!=JOptionPane.YES_OPTION) return;

				DAOBiosample.changeOwnership(biosamples, u, admin);
				SpiritChangeListener.fireModelChanged(SpiritChangeType.MODEL_UPDATED, Biosample.class, biosamples);
			} catch (Exception e) {
				JExceptionDialog.showError(e);
			} finally {
				JPAUtil.popEditableContext();
			}

		}
	}


	public static class Action_Find_Duplicate_Biosamples extends AbstractAction {
		public Action_Find_Duplicate_Biosamples() {
			super("Find Duplicated Biosamples");
			putValue(AbstractAction.MNEMONIC_KEY, (int)('d'));
			putValue(Action.SMALL_ICON, IconType.DUPLICATE.getIcon());
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			new BiosampleDuplicatesDlg();
		}
	}





	public static class Action_Checkin extends AbstractAction {
		private Collection<Biosample> biosamples;
		public Action_Checkin(Collection<Biosample> biosamples) {
			super();
			putValue(AbstractAction.MNEMONIC_KEY, (int)('c'));
			putValue(Action.SMALL_ICON, IconType.LOCATION.getIcon());

			this.biosamples = biosamples;
			boolean enabled = true;
			boolean haveLocation = false;
			if(biosamples!=null) for (Biosample b : biosamples) {
				if(b.getLocation()!=null) haveLocation = true;
				if(b.getId()<=0 || b.isAbstract() || !SpiritRights.canEdit(b, SpiritFrame.getUser())) {
					enabled = false; break;
				}
			}
			putValue(Action.NAME, (haveLocation? "Relocate " : "Checkin ") + (biosamples==null || biosamples.size()!=1?"Batch":""));
			setEnabled(enabled);
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				new CheckinDlg(biosamples, true);
			} catch (Exception ex) {
				JExceptionDialog.showError(ex);
			}

		}
	}





	public static class Action_Checkout extends AbstractAction {
		private Collection<Biosample> biosamples;

		public Action_Checkout(Collection<Biosample> biosamples) {
			super();
			this.biosamples = biosamples;
			putValue(AbstractAction.MNEMONIC_KEY, (int)('c'));
			putValue(Action.SMALL_ICON, IconType.LOCATION.getIcon());

			boolean enabled = biosamples!=null;
			boolean someLoc = false;
			if(biosamples!=null) for (Biosample b : biosamples) {
				if(b.getLocation()!=null) someLoc = true;
				if(!SpiritRights.canEdit(b, SpiritFrame.getUser())) {
					enabled = false; break;
				}
			}
			setEnabled(enabled && someLoc);
			putValue(Action.NAME,"Checkout " + (biosamples==null || biosamples.size()!=1?"Batch":""));
		}

		//		public Collection<Biosample> getBiosamples() {
		//			return biosamples;
		//		}

		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				new CheckoutDlg(biosamples);
			} catch (Exception ex) {
				JExceptionDialog.showError(ex);
			}

		}
	}





	//////////////////////////////////////////////////
	public static void attachPopup(final BiosampleTable table) {
		table.getInputMap().put(KeyStroke.getKeyStroke("control P"), "print");
		table.getActionMap().put("print", new Action_Print(null) {
			@Override
			public List<Biosample> getBiosamples() {
				return table.getSelection();
			}
		});
		table.addMouseListener(new PopupAdapter(table) {
			@Override
			protected void showPopup(MouseEvent e) {
				BiosampleActions.createPopup(new ArrayList<Biosample>(table.getSelection())).show(table, e.getX(), e.getY());
			}
		});
	}

	public static void attachRevisionPopup(final BiosampleTable table) {
		table.addMouseListener(new PopupAdapter(table) {
			@Override
			protected void showPopup(MouseEvent e) {

				List<Biosample> biosamples = table.getSelection();
				JPopupMenu popupMenu = new JPopupMenu();
				String s = biosamples!=null && biosamples.size()==1? biosamples.get(0).getSampleIdName() :"";
				popupMenu.add(new JCustomLabel("   Biosample: "+s, Font.BOLD));

				if(biosamples==null || biosamples.size()==0) {

				} else if(biosamples.size()==1) {
					Biosample b = biosamples.get(0);
					popupMenu.add(new JMenuItem(new AdminActions.Action_Restore(biosamples)));

					if( b!=null && b.getBiotype()!=null) {
						popupMenu.add(new JSeparator());
						popupMenu.add(new JMenuItem(new Action_History(biosamples)));
					}

				} else { //batch
					popupMenu.add(new JMenuItem(new AdminActions.Action_Restore(biosamples)));

				}

				popupMenu.show(table, e.getX(), e.getY());
			}
		});
	}

	public static void attachPopup(final PivotTable comp) {
		comp.addMouseListener(new PopupAdapter() {
			@Override
			protected void showPopup(MouseEvent e) {
				Collection<Biosample> biosamples = comp.getSelectedBiosamples();
				if(biosamples!=null) BiosampleActions.createPopup(new ArrayList<Biosample>(biosamples)).show(comp, e.getX(), e.getY());
			}
		});
	}
	public static void attachPopup(final JComponent comp) {
		comp.addMouseListener(new PopupAdapter() {
			@Override
			protected void showPopup(MouseEvent e) {
				Collection<Biosample> biosamples = null;
				if(comp instanceof IBiosampleDetail) {
					biosamples = ((IBiosampleDetail) comp).getBiosamples();
				}
				if(biosamples!=null) BiosampleActions.createPopup(new ArrayList<Biosample>(biosamples)).show(comp, e.getX(), e.getY());
			}
		});
	}


	/**
	 *
	 * @param biosamples
	 * @param phase
	 * @return
	 */
	public static JPopupMenu createPopup(List<Biosample> biosamples) {
		return SpiritFrame.getInstance().getPopupHelper().createBiosamplePopup(biosamples);
	}


}
