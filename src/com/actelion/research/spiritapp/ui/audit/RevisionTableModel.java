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

package com.actelion.research.spiritapp.ui.audit;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.JComponent;

import org.hibernate.envers.RevisionType;

import com.actelion.research.spiritcore.business.audit.Revision;
import com.actelion.research.util.FormatterUtils;
import com.actelion.research.util.ui.exceltable.AbstractExtendTable;
import com.actelion.research.util.ui.exceltable.Column;
import com.actelion.research.util.ui.exceltable.DateColumn;
import com.actelion.research.util.ui.exceltable.ExtendTableModel;
import com.actelion.research.util.ui.exceltable.IntegerColumn;
import com.actelion.research.util.ui.exceltable.JLabelNoRepaint;
import com.actelion.research.util.ui.exceltable.StringColumn;

public class RevisionTableModel extends ExtendTableModel<Revision> {

	//	private boolean addWhatColumn = false;
	//	private Map<Revision, String> changeMap = null;

	private IntegerColumn<Revision> revColumn = new IntegerColumn<Revision>("RevId") {
		@Override
		public Integer getValue(Revision row) {
			return row.getRevId();
		}
	};

	private StringColumn<Revision> userColumn = new StringColumn<Revision>("User") {
		@Override
		public String getValue(Revision row) {
			return row.getUser();
		}
	};

	private DateColumn<Revision> dateColumn = new DateColumn<Revision>("Date") {
		@Override
		public Date getValue(Revision row) {
			return row.getDate();
		}
		@Override
		public void postProcess(AbstractExtendTable<Revision> table, Revision row, int rowNo, Object value, JComponent comp) {
			((JLabelNoRepaint)comp).setText(FormatterUtils.formatDateTime((Date)value));
		}
	};


	//	private StringColumn<Revision> studyColumn = new StringColumn<Revision>("Study") {
	//		@Override
	//		public String getValue(Revision row) {
	//			return row.getStudy()==null?"": row.getStudy().getStudyId();
	//		}
	//	};

	private StringColumn<Revision> whatColumn = new StringColumn<Revision>("What") {
		@Override
		public String getValue(Revision row) {
			return row.getWhat().replace(", ", "\n");
		}
		@Override
		public void postProcess(AbstractExtendTable<Revision> table, Revision rev, int rowNo, Object value, JComponent comp) {
			if(rev.getRevisionType()==RevisionType.ADD) {
				comp.setForeground(new Color(0, 80, 0));
			} else if(rev.getRevisionType()==RevisionType.DEL) {
				comp.setForeground(new Color(170, 0, 0));
			} else {
				comp.setForeground(new Color(150, 100, 0));
			}
		}
		@Override
		public boolean isMultiline() {
			return true;
		}
	};

	private StringColumn<Revision> reasonColumn = new StringColumn<Revision>("Reason") {
		@Override
		public String getValue(Revision row) {
			return row.getReason();
		}
		@Override
		public boolean isMultiline() {return true;}
	};

	private StringColumn<Revision> changeColumn = new StringColumn<Revision>("Change") {
		@Override
		public String getValue(Revision row) {
			return row.getDifference().replace(", ", "\n");
		}

		@Override
		public void postProcess(AbstractExtendTable<Revision> table, Revision rev, int rowNo, Object value, JComponent comp) {
			if(rev.getRevisionType()==RevisionType.ADD) {
				comp.setForeground(new Color(0, 80, 0));
			} else if(rev.getRevisionType()==RevisionType.DEL) {
				comp.setForeground(new Color(170, 0, 0));
			} else {
				comp.setForeground(new Color(150, 100, 0));
			}
		}

		@Override
		public boolean isMultiline() {return true;}
	};

	public RevisionTableModel() {
		initColumns();
	}

	public void initColumns() {
		List<Column<Revision, ?>> allColumns = new ArrayList<>();
		allColumns.add(COLUMN_ROWNO);
		allColumns.add(revColumn.setHideable(true));
		allColumns.add(userColumn);
		allColumns.add(dateColumn);
		//		allColumns.add(studyColumn);
		allColumns.add(whatColumn);
		allColumns.add(changeColumn);
		allColumns.add(reasonColumn);
		setColumns(allColumns);
		showAllHideable(true);
	}

	@Override
	public void setRows(List<Revision> rows) {
		super.setRows(rows);
	}

	//	public void setChangeMap(Map<Revision, String> changeMap) {
	//		this.changeMap = changeMap;
	//	}
	//
	//	public Map<Revision, String> getChangeMap() {
	//		return changeMap;
	//	}
}
