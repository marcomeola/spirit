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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.actelion.research.spiritapp.ui.biosample.column.BiosampleElbColumn;
import com.actelion.research.spiritapp.ui.biosample.column.BiosampleQualityColumn;
import com.actelion.research.spiritapp.ui.biosample.column.BiotypeColumn;
import com.actelion.research.spiritapp.ui.biosample.column.ChildrenColumn;
import com.actelion.research.spiritapp.ui.biosample.column.CombinedColumn;
import com.actelion.research.spiritapp.ui.biosample.column.ContainerAmountColumn;
import com.actelion.research.spiritapp.ui.biosample.column.ContainerFullColumn;
import com.actelion.research.spiritapp.ui.biosample.column.ContainerLocationPosColumn;
import com.actelion.research.spiritapp.ui.biosample.column.CreationColumn;
import com.actelion.research.spiritapp.ui.biosample.column.ExpiryDateColumn;
import com.actelion.research.spiritapp.ui.biosample.column.LastChangeColumn;
import com.actelion.research.spiritapp.ui.biosample.column.ParentBiosampleColumn;
import com.actelion.research.spiritapp.ui.biosample.column.ResultColumn;
import com.actelion.research.spiritapp.ui.biosample.column.ScannedPosColumn;
import com.actelion.research.spiritapp.ui.biosample.column.StatusColumn;
import com.actelion.research.spiritapp.ui.biosample.column.StudyGroupColumn;
import com.actelion.research.spiritapp.ui.biosample.column.StudyIdColumn;
import com.actelion.research.spiritapp.ui.biosample.column.StudyParticipantIdColumn;
import com.actelion.research.spiritapp.ui.biosample.column.StudyPhaseColumn;
import com.actelion.research.spiritapp.ui.biosample.column.StudySamplingColumn;
import com.actelion.research.spiritapp.ui.biosample.column.StudySubGroupColumn;
import com.actelion.research.spiritapp.ui.biosample.column.StudyTreatmentColumn;
import com.actelion.research.spiritapp.ui.biosample.linker.AbstractLinkerColumn;
import com.actelion.research.spiritapp.ui.biosample.linker.LinkerColumnFactory;
import com.actelion.research.spiritapp.ui.biosample.linker.SampleIdColumn;
import com.actelion.research.spiritapp.ui.util.component.SpiritExtendTableModel;
import com.actelion.research.spiritcore.business.DataType;
import com.actelion.research.spiritcore.business.biosample.Biosample;
import com.actelion.research.spiritcore.business.biosample.BiosampleLinker;
import com.actelion.research.spiritcore.business.biosample.BiosampleLinker.LinkerType;
import com.actelion.research.spiritcore.business.biosample.Biotype;
import com.actelion.research.spiritcore.business.biosample.BiotypeCategory;
import com.actelion.research.spiritcore.business.biosample.BiotypeMetadata;
import com.actelion.research.spiritcore.business.biosample.ContainerType;
import com.actelion.research.spiritcore.business.property.PropertyKey;
import com.actelion.research.spiritcore.business.study.Study;
import com.actelion.research.spiritcore.services.dao.DAOBiotype;
import com.actelion.research.spiritcore.services.dao.JPAUtil;
import com.actelion.research.spiritcore.services.dao.SpiritProperties;
import com.actelion.research.util.ui.JExceptionDialog;
import com.actelion.research.util.ui.exceltable.Column;

/**
 * The model should be formatted in this order - rowNo, elb (sortOrder: 1.#) -
 * study infos: group, phase (sortOrder: 2.#) - container: id, amount, location
 * (sortOrder: 3.#) - top, parents: sampleId + linked infos (sortOrder: 4.#) -
 * sampleId + metadata + comments: combined or specific (sortOrder: 6.#) -
 * calculated data (sortOrder: 8.#) - misc (sortOrder: 9.#) - owner: user, date
 * (sortOrder: 10.#)
 *
 * @author freyssj
 *
 */
public class BiosampleTableModel extends SpiritExtendTableModel<Biosample> {


	public enum Mode {
		SHORT, //no container
		COMPACT, //combined columns
		FULL
	}

	/**
	 * Is the view made for a special biotype or for any generic sample
	 * (type==null)
	 */
	private Biotype type;

	//For the lastChange column, here is the max revId to be used.
	private int revId;

	private Mode mode = Mode.FULL;

	private boolean filterTrashed = false;

	private Set<BiosampleLinker> linkers;

	public BiosampleTableModel() {
		addTableModelListener(e-> linkers = null);
	}

	public void initColumns() {

		Set<Biosample> myRows = new HashSet<>(getRows());
		Set<Biotype> types = Biosample.getBiotypes(myRows);
		Set<Biotype> parentTypes = new HashSet<>();
		Set<Biotype> topBiotypes = new HashSet<>();
		Set<Study> studies = Biosample.getStudies(getRows());

		if (types.size() == 1) {
			this.type = types.iterator().next();
		} else {
			this.type = null;
		}
		boolean someLiving = false;
		for (Biotype biotype : types) {
			if(biotype.getCategory()==BiotypeCategory.LIVING) {
				someLiving = true; break;
			}
		}

		boolean hasPhases = false;
		boolean hasDifferentTop = false;
		boolean hasContainers = false;
		boolean hasScannedPos = getRows().size()>0 && getRows().get(0).getScannedPosition()!=null;
		for (Biosample b : getRows()) {
			if (b == null) {
				continue;
			}
			if (b.getInheritedPhase() != null) {
				hasPhases = true;
			}
			if ((b.getContainerType()!=null && b.getContainerType()!=ContainerType.UNKNOWN) || b.getContainerId()!=null || b.getLocation()!=null || b.getAmountAndUnit()!=null) {
				hasContainers = true;
			}
			if (!myRows.contains(b.getTopParentInSameStudy())) {
				hasDifferentTop = true;
				topBiotypes.add(b.getTopParentInSameStudy().getBiotype());
			}
			if (b.getParent() != null && !b.getParent().equals(b.getTopParentInSameStudy()) && !myRows.contains(b.getParent())) {
				parentTypes.add(b.getParent().getBiotype());
			}
		}

		List<Column<Biosample, ?>> columns = new ArrayList<>();

		// Generic info
		columns.add(COLUMN_ROWNO);
		if(mode==Mode.FULL) columns.add(new BiosampleElbColumn());
		if(hasScannedPos) columns.add(new ScannedPosColumn());

		// Container Info
		if (hasContainers && (type == null || !type.isAbstract())) {
			if(mode!=Mode.SHORT && type!=null && type.isHideContainer()) {
				columns.add(new ContainerLocationPosColumn());
				if(type.getAmountUnit()!=null) {
					columns.add(new ContainerAmountColumn(type));
				}
			} else {
				ContainerFullColumn col = new ContainerFullColumn();
				col.setHideable(mode==Mode.SHORT);
				columns.add(col);
			}
		}

		// Study columns
		if (mode==Mode.FULL && (studies.size()>0 || someLiving)) {
			columns.add(new StudyIdColumn());
			if(SpiritProperties.getInstance().isChecked(PropertyKey.STUDY_FEATURE_STUDYDESIGN)) {
				columns.add(new StudyGroupColumn());
				columns.add(new StudySubGroupColumn());
			}
		}

		// Top
		if (hasDifferentTop && SpiritProperties.getInstance().isAdvancedMode()) {
			if(studies.size()>0) {
				columns.add(new StudyParticipantIdColumn());
			} else if (topBiotypes.size() == 1) {
				Biotype topBiotype = topBiotypes.iterator().next();
				boolean topDifferentLinker = false;
				BiosampleLinker linker = new BiosampleLinker(topBiotype, LinkerType.SAMPLEID);
				for (Biosample b : rows) {
					if(!b.getTopParent().equals(linker.getLinked(b))) {
						topDifferentLinker = true;
						break;
					}
				}
				if(topDifferentLinker) {
					columns.add(new StudyParticipantIdColumn());
				} else {
					columns.add(LinkerColumnFactory.create(linker));
				}
			} else {
				columns.add(new StudyParticipantIdColumn());
			}
		}
		// Parent? only if parent <> topparent and parent.type <> this.type and parentType not in types
		if (parentTypes.size() == 1) {
			Biotype parentType = parentTypes.iterator().next();
			if (hasDifferentTop && !parentType.equals(topBiotypes.iterator().next()) /*&& !types.contains(parentType)*/) {
				if(!types.contains(parentType)) {
					columns.add(LinkerColumnFactory.create(new BiosampleLinker(parentType, LinkerType.SAMPLEID)));
				} else {
					columns.add(new ParentBiosampleColumn(null));
				}
			}
		}

		if (mode!=Mode.SHORT && hasPhases) {
			columns.add(new StudyPhaseColumn());
		}

		// SampleId/Name
		Column<Biosample, ?> sampleIdColumn;
		if(type!=null && (type.getCategory()==BiotypeCategory.LIBRARY || !type.isHideSampleId()) && type.getSampleNameLabel()!=null) {
			sampleIdColumn = new SampleIdColumn(new BiosampleLinker(LinkerType.SAMPLEID, type), false, true);
			columns.add(sampleIdColumn);
			columns.add(LinkerColumnFactory.create(new BiosampleLinker(LinkerType.SAMPLENAME, type)));
		} else {
			sampleIdColumn = LinkerColumnFactory.create(new BiosampleLinker(LinkerType.SAMPLEID, type));
			columns.add(sampleIdColumn);
		}

		if (mode==Mode.COMPACT || type == null) {
			// Combine all Metadata in one column
			columns.add(new BiotypeColumn());
			columns.add(new CombinedColumn());
		} else {
			// Expand Metadata
			for (BiotypeMetadata t : type.getMetadata()) {
				if (t.getDataType() == DataType.BIOSAMPLE) {
					columns.add(LinkerColumnFactory.create(new BiosampleLinker(t, LinkerType.SAMPLEID, DAOBiotype.getBiotype(t.getParameters()))));
				} else {
					columns.add(LinkerColumnFactory.create(new BiosampleLinker(t)));
				}
			}
			// comments
			columns.add(LinkerColumnFactory.create(new BiosampleLinker(LinkerType.COMMENTS, type)));
		}

		if(SpiritProperties.getInstance().isChecked(PropertyKey.STUDY_FEATURE_STUDYDESIGN)) {
			//Sampling
			columns.add(new StudySamplingColumn());
		}

		//Expiry
		{
			ExpiryDateColumn col = new ExpiryDateColumn();
			col.setHideable(mode!=Mode.FULL);
			columns.add(col);
		}

		// CreatedBy
		CreationColumn col = new CreationColumn(true);
		col.setHideable(mode!=Mode.FULL);
		columns.add(col);


		//Optional columns
		columns.add(new LastChangeColumn(revId));
		if(SpiritProperties.getInstance().isChecked(PropertyKey.STUDY_FEATURE_STUDYDESIGN)) {
			columns.add(new StudyTreatmentColumn());
		}
		columns.add(new BiosampleQualityColumn());
		columns.add(new StatusColumn());
		if(SpiritProperties.getInstance().isAdvancedMode()) {
			columns.add(new ChildrenColumn());
		}
		if(SpiritProperties.getInstance().isChecked(PropertyKey.TAB_RESULT)) {
			columns.add(new ResultColumn());
		}
		columns.add(new CreationColumn(false));

		columns = removeEmptyColumns(columns);
		sortColumns(columns);

		setTreeColumn(sampleIdColumn);
		setColumns(columns);
	}

	@Override
	public Biosample getTreeParent(Biosample row) {
		if (row == null) {
			return null;
		} else {
			try {
				return row.getParent();
			} catch (Throwable e) {
				row = JPAUtil.reattach(row);
				try {
					System.err.println("Failed lazy init in getTreeChildren > Reload [" + e + "]");
					return row.getParent();
				} catch(Exception ex) {
					JExceptionDialog.showError(ex);
					return null;
				}
			}

		}
	}

	@Override
	public Collection<Biosample> getTreeChildren(Biosample row) {
		if (row == null) {
			return new ArrayList<>();
		} else {
			try {
				Set<Biosample> children = row.getChildren();
				List<Biosample> res = new ArrayList<>();
				for (Biosample b : children) {
					if(!filterTrashed || b.getStatus()==null || b.getStatus().isAvailable()) res.add(b);
				}
				return res;

			} catch (Throwable e) {

				System.err.println("Failed lazy init in getTreeChildren > Reload [" + e + "]");
				row = JPAUtil.reattach(row);
				try {
					List<Biosample> res = new ArrayList<>();
					for (Biosample b : row.getChildren()) {
						if(!filterTrashed || b.getStatus()==null || b.getStatus().isAvailable()) res.add(b);
					}
					return res;
				} catch(Exception ex) {
					JExceptionDialog.showError(ex);
					return new ArrayList<Biosample>();
				}
			}
		}
	}

	public int getRevId() {
		return revId;
	}

	public void setRevId(int revId) {
		this.revId = revId;
	}

	@Override
	public Column<Biosample, ?> getTreeColumn() {
		if(!SpiritProperties.getInstance().isAdvancedMode()) return null;

		for (Column<Biosample, ?> c : getColumns()) {
			if ((c instanceof SampleIdColumn) && !((SampleIdColumn) c).getLinker().isLinked()) {
				return c;
			}
		}
		return null;
	}

	/**
	 * Returns all the linked data from the biosamples
	 * @return
	 */
	public Set<BiosampleLinker> getLinkers() {
		if (linkers == null) {
			linkers = new HashSet<>();
			for (Column<Biosample, ?> col : getColumns()) {
				if (col instanceof AbstractLinkerColumn<?>) {
					linkers.add(((AbstractLinkerColumn<?>) col).getLinker());
				}
			}
		}
		return linkers;
	}

	public Biotype getBiotype() {
		return type;
	}

	@Override
	public void addColumn(Column<Biosample, ?> column) {
		linkers = null;
		super.addColumn(column);
	}

	@Override
	public void removeColumn(Column<Biosample, ?> column) {
		linkers = null;
		super.removeColumn(column);
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public void setFilterTrashed(boolean filterTrashed) {
		this.filterTrashed = filterTrashed;
	}
}
