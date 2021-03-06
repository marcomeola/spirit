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

package com.actelion.research.spiritapp.ui.location.depictor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import com.actelion.research.spiritcore.business.biosample.Container;
import com.actelion.research.spiritcore.business.location.Location;

public interface RackDepictorRenderer {
	public Color getWellBackground(Location location, int pos, Container c);
	public void paintWellPre(RackDepictor depictor, Graphics2D g, Location location, int pos, Container c, Rectangle r);
	public void paintWell(RackDepictor depictor, Graphics2D g, Location location, int pos, Container c, Rectangle r);
	public void paintWellPost(RackDepictor depictor, Graphics2D g, Location location, int pos, Container c, Rectangle r);
}
