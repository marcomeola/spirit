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

package com.actelion.research.spiritapp.ui.biosample.editor;

import java.awt.Component;
import java.util.Date;

import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.TableCellEditor;

import com.actelion.research.spiritapp.ui.biosample.MetadataComponentFactory.DateStringComponent;
import com.actelion.research.util.FormatterUtils;

public class DateCellEditor extends AbstractCellEditor implements TableCellEditor {

	private DateStringComponent tf = new DateStringComponent(false);

	public DateCellEditor() {
		tf.setBorder(UIManager.getBorder("Table.focusCellHighlightBorder"));			
	}
	
	
	/**
	 * Convert the format yyyyMMdd to the UI format
	 */
	@Override
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
		tf.setData(FormatterUtils.formatDateYyyy( (Date) value));
		tf.selectAll();
		return tf;
	}

	/**
	 * Converts to the DB format
	 */
	@Override		
	public Date getCellEditorValue() {
		Date t = FormatterUtils.parseDateTime(tf.getData());
		return t;
	}				
}