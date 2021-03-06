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

package com.actelion.research.spiritapp.ui.util.component;

import java.awt.Component;

import javax.swing.JLabel;

import com.actelion.research.spiritcore.business.Quality;
import com.actelion.research.util.ui.JObjectComboBox;

public class QualityComboBox extends JObjectComboBox<Quality> {

	public QualityComboBox() {
		super(Quality.values());
		setTextWhenEmpty("Quality");
	}

	@Override
	public Component processCellRenderer(JLabel comp, String value, int index) {
		Quality quality = getMap().get(value);
		if(quality==null) {
			comp.setBackground(null);
		} else {
			comp.setBackground(quality.getBackground());
		}
		return comp;
	}

}
