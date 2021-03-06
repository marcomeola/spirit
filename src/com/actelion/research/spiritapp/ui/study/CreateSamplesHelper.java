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

package com.actelion.research.spiritapp.ui.study;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.slf4j.LoggerFactory;

import com.actelion.research.spiritapp.Spirit;
import com.actelion.research.spiritapp.ui.SpiritFrame;
import com.actelion.research.spiritapp.ui.biosample.BiosampleTable;
import com.actelion.research.spiritapp.ui.biosample.column.StudySamplingColumn;
import com.actelion.research.spiritapp.ui.util.SpiritChangeListener;
import com.actelion.research.spiritapp.ui.util.SpiritChangeType;
import com.actelion.research.spiritcore.business.biosample.Biosample;
import com.actelion.research.spiritcore.business.biosample.Biosample.HierarchyMode;
import com.actelion.research.spiritcore.business.biosample.Biosample.InfoFormat;
import com.actelion.research.spiritcore.business.biosample.Status;
import com.actelion.research.spiritcore.business.property.PropertyKey;
import com.actelion.research.spiritcore.business.study.Study;
import com.actelion.research.spiritcore.services.SpiritRights;
import com.actelion.research.spiritcore.services.dao.DAOBarcode;
import com.actelion.research.spiritcore.services.dao.DAOBiosample;
import com.actelion.research.spiritcore.services.dao.JPAUtil;
import com.actelion.research.spiritcore.services.dao.SpiritProperties;
import com.actelion.research.spiritcore.services.helper.BiosampleCreationHelper;
import com.actelion.research.spiritcore.util.ListHashMap;
import com.actelion.research.util.ui.FastFont;
import com.actelion.research.util.ui.JCustomLabel;
import com.actelion.research.util.ui.SwingWorkerExtended;
import com.actelion.research.util.ui.UIUtils;


/**
 * Helper Class used to synchronize the samples based on a study design.
 *
 * @author Joel Freyss
 */
public class CreateSamplesHelper {


	/**
	 * After each modification of the sampling in the study design, the samples have to be created, this is checked and done through this function.
	 * This function has to be called everytime something has to be done on the samples (the user may not understand the different between the design and the concrete object)
	 *
	 * Read access is necessary
	 * The study must allow the synchronization of samples
	 *
	 *
	 * @param study
	 * @return boolean - false if the study is not consistent and the user refused the creation of objects, true if it it consistent
	 */
	public static boolean synchronizeSamples(Study study) throws Exception {

		if(study==null) return true;
		if(!SpiritProperties.getInstance().isChecked(PropertyKey.STUDY_FEATURE_STUDYDESIGN)) return true;
		if(!study.isSynchronizeSamples()) return true;

		//Check user rights, read access is enough, because this function only mimics what is in the design.
		if(!SpiritRights.canBlind(study, SpiritFrame.getUser())) throw new Exception("You must have blind/expert access rights on "+study);

		try {
			JPAUtil.pushEditableContext(SpiritFrame.getUser());

			study = JPAUtil.reattach(study);


			final List<Biosample> toAdd = new ArrayList<>();
			final List<Biosample> toUpdate = new ArrayList<>();
			final List<Biosample> toQuestionable = new ArrayList<>();
			final List<Biosample> toDelete = new ArrayList<>();

			//check existence of samples from sampling template
			List<Biosample> allNeeded = BiosampleCreationHelper.processTemplateInStudy(study, null, null, null, null);
			LoggerFactory.getLogger(CreateSamplesHelper.class).debug("allNeeded="+allNeeded);

			////////////////////////////////////////

			//First filter: Find which ones must be deleted or created based on their status and their existence
			for (Biosample b : allNeeded) {
				boolean dead = b.getTopParentInSameStudy().getStatus()==Status.KILLED || b.getTopParentInSameStudy().getStatus()==Status.DEAD;
				if(dead && b.isDeadAt(b.getInheritedPhase())) {
					//Remove samples from animals marked as Found Dead or Killed, but keep Animals marked as Necropsied
					if(b.getId()>0) {
						toDelete.add(b);
					}
				} else {
					//Add other
					if(b.getId()<=0) {
						toAdd.add(b);
					}
				}
			}

			//Create Barcodes
			for (Biosample b : toAdd) {
				if(b.getSampleId()==null || b.getSampleId().length()==0) {
					b.setSampleId(DAOBarcode.getNextId(b));
				}
			}

			////////////////////////////////////////
			//Check samples to be deleted
			for(Biosample top: study.getParticipantsSorted()) {
				//Skip dead/necropsied/...
				if(!top.getStatus().isAvailable()) continue;

				for(Biosample sample: top.getHierarchy(HierarchyMode.ATTACHED_SAMPLES)) {
					if(sample.getInheritedGroup()==null) continue; //Reserve -> Skip
					if(sample.getInheritedPhase()==null) continue; //No Phase -> Skip
					if(sample.getAttachedSampling()==null) continue; //No sampling -> Skip (should not happen because we loop through attached samples)
					if(!allNeeded.contains(sample)) toDelete.add(sample);
				}
			}


			//Find samples that may have been moved.
			//find samples where the topSample may have been moved to a new group/subgroup: ie there is a sample to delete, matching a sample in toadd, with the same biotype, metadata
			ListHashMap<Biosample, Biosample> top2toAddSamples = new ListHashMap<>();
			for (Biosample b : toAdd) {
				top2toAddSamples.add(b.getTopParentInSameStudy(), b);
			}
			for (Biosample b : new ArrayList<>(toDelete)) {
				List<Biosample> matches = new ArrayList<>();
				assert b!=null;
				assert b.getTopParentInSameStudy()!=null;
				if(top2toAddSamples.get(b.getTopParentInSameStudy())!=null) {
					for (Biosample b2 : top2toAddSamples.get(b.getTopParentInSameStudy())) {

						if(!b.getAttachedSampling().equals(b2.getAttachedSampling())) continue;
						if(!b.getBiotype().equals(b2.getBiotype())) continue;
						String cmt1 = b.getInfos(EnumSet.of(InfoFormat.METATADATA, InfoFormat.COMMENTS));
						String cmt2 = b2.getInfos(EnumSet.of(InfoFormat.METATADATA, InfoFormat.COMMENTS));
						if(!cmt1.equals(cmt2)) continue;
						matches.add(b2);
					}
				}
				if(matches.size()==1) {
					//Replace the sample with the match (and don't forget the children)
					Biosample match = matches.get(0);
					b.setInheritedPhase(match.getInheritedPhase());
					toAdd.remove(match);
					toDelete.remove(b);
					toUpdate.add(b);

					for (Biosample child :  new ArrayList<>(match.getChildren())) {
						child.setParent(b);
					}

				} else if(matches.size()>1){
					//There are matches but this is not clear
					//Unresolvable problems
					toQuestionable.addAll(matches);
					toQuestionable.add(b);
				}

			}
			toAdd.removeAll(toQuestionable);
			toAdd.removeAll(toUpdate);
			toDelete.removeAll(toQuestionable);
			toDelete.removeAll(toUpdate);

			LoggerFactory.getLogger(CreateSamplesHelper.class).debug("toAdd="+toAdd.size()+" toDelete="+toDelete.size()+" questionable="+toQuestionable.size());

			if(toAdd.size()==0 && toUpdate.size()==0 && toDelete.size()==0)  return true;


			//Main Panel
			Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
			JPanel contentPanel = new JPanel(new GridLayout(0, 1));
			contentPanel.setPreferredSize(new Dimension(Math.min(1200, dim.width-200), Math.min((toAdd.size() + toUpdate.size() + toDelete.size()) * 22 + 400, dim.height-300)));

			//Propose Creation
			JPanel creationPanel = new JPanel(new BorderLayout());
			if(toAdd.size()>0) {
				Collections.sort(toAdd);
				BiosampleTable table = new BiosampleTable();
				table.getModel().showHideable(new StudySamplingColumn(), true);
				table.setRows(toAdd);

				creationPanel.setBorder(BorderFactory.createEtchedBorder());
				creationPanel.add(BorderLayout.NORTH, UIUtils.createHorizontalBox(BorderFactory.createEmptyBorder(5, 0, 5, 0), new JCustomLabel("Based on your study design, those " +toAdd.size()+ " samples should be added.", FastFont.BIGGER), Box.createHorizontalGlue()));
				creationPanel.add(BorderLayout.CENTER, new JScrollPane(table));
				creationPanel.setBackground(Color.GREEN);
				contentPanel.add(creationPanel);
			}

			//Propose Update
			JPanel updatePanel = new JPanel(new BorderLayout());
			if(toUpdate.size()>0) {
				Collections.sort(toUpdate);
				BiosampleTable table = new BiosampleTable();
				table.getModel().showHideable(new StudySamplingColumn(), true);
				table.setRows(toUpdate);

				updatePanel.setBorder(BorderFactory.createEtchedBorder());
				updatePanel.add(BorderLayout.NORTH, UIUtils.createHorizontalBox(BorderFactory.createEmptyBorder(5, 0, 5, 0), new JCustomLabel("Based on your study design, those " +toUpdate.size()+ " samples should be moved to an other phase. ", FastFont.BIGGER), Box.createHorizontalGlue()));
				updatePanel.add(BorderLayout.CENTER, new JScrollPane(table));
				updatePanel.setBackground(Color.PINK);
				contentPanel.add(updatePanel);
			}

			//Propose Deletion
			JPanel questionablePanel = new JPanel(new BorderLayout());
			if(toQuestionable.size()>0) {
				//DeletionPanel
				Collections.sort(toQuestionable);
				final BiosampleTable table = new BiosampleTable();
				table.getModel().showHideable(new StudySamplingColumn(), true);
				table.setRows(toQuestionable);

				questionablePanel.setBorder(BorderFactory.createEtchedBorder());
				questionablePanel.add(BorderLayout.NORTH, UIUtils.createHorizontalBox(new JCustomLabel("Based on your study design, " +toDelete.size()+ " samples are questionable and will be ignored.", FastFont.BIGGER), Box.createHorizontalGlue()));
				questionablePanel.add(BorderLayout.CENTER, new JScrollPane(table));
				questionablePanel.setOpaque(true);
				questionablePanel.setBackground(Color.ORANGE);
				contentPanel.add(questionablePanel);
			}

			//Propose Deletion
			JPanel deletionPanel = new JPanel(new BorderLayout());
			final JCheckBox ignoreDeletionCheckBox = new JCheckBox("Continue without deleting those samples", false); //TODO set to false if labels are printed
			ignoreDeletionCheckBox.setVisible(toAdd.size()+toUpdate.size()>0 && toDelete.size()>0);
			if(toDelete.size()>0) {
				//DeletionPanel
				Collections.sort(toDelete);
				final BiosampleTable deletionTable = new BiosampleTable();
				deletionTable.getModel().showHideable(new StudySamplingColumn(), true);
				deletionTable.setRows(toDelete);

				deletionPanel.setBorder(BorderFactory.createEtchedBorder());
				deletionPanel.add(BorderLayout.NORTH,
						UIUtils.createVerticalBox(BorderFactory.createEmptyBorder(5, 0, 5, 0),
								UIUtils.createHorizontalBox(new JCustomLabel("Based on your study design, " +toDelete.size()+ " samples will be DELETED.", FastFont.BIGGER), Box.createHorizontalGlue()),
								UIUtils.createHorizontalBox(ignoreDeletionCheckBox, Box.createHorizontalGlue())));
				deletionPanel.add(BorderLayout.CENTER, new JScrollPane(deletionTable));
				deletionPanel.setOpaque(true);
				deletionPanel.setBackground(Color.RED);
				contentPanel.add(deletionPanel);
			}

			int res = JOptionPane.showOptionDialog(UIUtils.getMainFrame(),
					contentPanel,
					"Sample synchronization",
					JOptionPane.YES_NO_CANCEL_OPTION,
					JOptionPane.PLAIN_MESSAGE,
					null,
					new String[] {"Synchronize Samples", "Continue without synchronization", "Cancel"}, toDelete.size()>0? "Continue without synchronization": "Synchronize Samples");
			if(res==0) {
				new SwingWorkerExtended("Synchronize Samples", null, SwingWorkerExtended.FLAG_SYNCHRONOUS) {
					@Override
					protected void doInBackground() throws Exception {
						//Open the transaction
						EntityManager session = JPAUtil.getManager();
						EntityTransaction txn = null;
						try {
							txn = session.getTransaction();
							txn.begin();
							if(toUpdate.size()+toAdd.size()>0) {
								List<Biosample> toSave = new ArrayList<>();
								toSave.addAll(toUpdate);
								toSave.addAll(toAdd);
								DAOBiosample.persistBiosamples(session, toSave, Spirit.askForAuthentication());
							}


							if(toDelete.size()>0 && !ignoreDeletionCheckBox.isSelected()) {
								DAOBiosample.deleteBiosamples(session, toDelete, Spirit.askForAuthentication());
							}
							txn.commit();
							txn = null;

						} catch (Exception e) {
							if (txn != null)try {txn.rollback();} catch (Exception e2) {e2.printStackTrace();}
							throw e;
						}
					}
					@Override
					protected void done() {
						SpiritChangeListener.fireModelChanged(SpiritChangeType.MODEL_ADDED, Biosample.class, toAdd);
						SpiritChangeListener.fireModelChanged(SpiritChangeType.MODEL_DELETED, Biosample.class, toDelete);
					}
				};




			} else if(res==1) {
				//Nothing
			} else {
				//Canceled
				return false;
			}

			return true;


		} finally {
			JPAUtil.popEditableContext();
		}

	}

}
