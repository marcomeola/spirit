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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import com.actelion.research.spiritapp.ui.IStudyTab;
import com.actelion.research.spiritapp.ui.SpiritFrame;
import com.actelion.research.spiritapp.ui.SpiritTab;
import com.actelion.research.spiritapp.ui.util.SpiritChangeType;
import com.actelion.research.spiritapp.ui.util.component.JBGScrollPane;
import com.actelion.research.spiritcore.business.study.Study;
import com.actelion.research.spiritcore.business.study.StudyQuery;
import com.actelion.research.spiritcore.services.dao.DAOStudy;
import com.actelion.research.spiritcore.services.dao.JPAUtil;
import com.actelion.research.spiritcore.util.MiscUtils;
import com.actelion.research.util.ui.SwingWorkerExtended;
import com.actelion.research.util.ui.exceltable.JSplitPaneWithZeroSizeDivider;
import com.actelion.research.util.ui.iconbutton.IconType;

public class StudyTab extends SpiritTab implements IStudyTab {

	private final StudyTable studyTable = new StudyTable();
	private final StudySearchPane searchPane;

	private final StudyDetailPanel detailPane = new StudyDetailPanel(JSplitPane.HORIZONTAL_SPLIT);

	private boolean initialized = false;

	public StudyTab(SpiritFrame frame) {
		super(frame, "Studies", IconType.STUDY.getIcon());
		searchPane = new StudySearchPane(frame, studyTable);
		final JScrollPane studyScrollPane = new JBGScrollPane(studyTable, 3);


		JSplitPane northPane = new JSplitPaneWithZeroSizeDivider(JSplitPane.HORIZONTAL_SPLIT, searchPane, studyScrollPane);
		northPane.setDividerLocation(250);

		JSplitPane contentPane = new JSplitPaneWithZeroSizeDivider(JSplitPane.VERTICAL_SPLIT, northPane, detailPane);
		contentPane.setDividerLocation(250);

		detailPane.showInfos();

		studyTable.getSelectionModel().addListSelectionListener(e-> {
			if(e.getValueIsAdjusting()) return;
			final List<Study> sel = studyTable.getSelection();
			Study study = sel.size()==1? sel.get(0): null;
			study = JPAUtil.reattach(study);
			detailPane.setStudy(study);
			frame.setStudyId(MiscUtils.flatten(Study.getStudyIds(sel)));
		});


		StudyActions.attachPopup(studyTable);
		StudyActions.attachPopup(studyScrollPane);
		StudyActions.attachPopup(detailPane);

		searchPane.addPropertyChangeListener(evt-> {
			StudyTab.this.firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
		});

		setLayout(new BorderLayout());
		add(BorderLayout.CENTER, contentPane);


	}

	@Override
	public<T> void fireModelChanged(SpiritChangeType action, Class<T> what, Collection<T> details) {
		if(!isShowing()) return;
		if(action==SpiritChangeType.MODEL_DELETED && what==Study.class) {
			studyTable.getModel().getRows().removeAll(details);
			studyTable.getModel().fireTableDataChanged();
			detailPane.setStudy(null);
		} else if((action==SpiritChangeType.MODEL_UPDATED || action==SpiritChangeType.MODEL_ADDED) && what==Study.class) {
			getFrame().setStudyId(((Study)details.iterator().next()).getStudyId());
			for (T study : details) {
				Study s = (Study) study;
				studyTable.getRows().replaceAll(r -> r.equals(s)? s: r);
				studyTable.getModel().fireTableDataChanged();
			}
		}

	}

	@Override
	public void setStudy(Study study) {
		getFrame().setStudyId(study==null?"": study.getStudyId());
		searchPane.query().afterDone(() -> {
			studyTable.setSelection(study==null? null: Collections.singleton(study));
		});
	}

	@Override
	public Study getStudy() {
		return detailPane.getStudy()==null? null: detailPane.getStudy();
	}

	/**
	 * Gets either the selected studies (if >1 selected) or all retrieved studies
	 */
	@Override
	public List<Study> getStudies() {
		return studyTable.getSelection().size()>1? studyTable.getSelection(): studyTable.getRows();
	}

	@Override
	public void onTabSelect() {
		onStudySelect();
		if(getRootPane()!=null){
			getRootPane().setDefaultButton(searchPane.getSearchButton());
			if(!initialized) {
				searchPane.reset();
				initialized = true;
			}
		}
	}

	@Override
	public void onStudySelect() {
		String studyIds = getFrame().getStudyId();
		if(studyIds==null || studyIds.length()==0) return;

		this.initialized = true;

		//Execute this thread after the others
		new SwingWorkerExtended("Loading Studies", studyTable, SwingWorkerExtended.FLAG_ASYNCHRONOUS100MS) {
			List<Study> studies;
			@Override
			protected void doInBackground() throws Exception {
				StudyQuery q = new StudyQuery();
				q.setStudyIds(studyIds);
				studies = DAOStudy.queryStudies(q, SpiritFrame.getUser());
			}
			@Override
			protected void done() {
				if(!studyTable.getRows().containsAll(studies)) {
					studyTable.setRows(studies);
				}
				studyTable.setSelection(studies);
			}
		};
	}


}
