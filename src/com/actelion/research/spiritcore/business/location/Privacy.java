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

package com.actelion.research.spiritcore.business.location;

import java.awt.Color;

public enum Privacy {
	INHERITED("Inherited", "Same as the parent", Color.BLACK, Color.WHITE),
	PUBLIC("Public", null, new Color(0,120,0), new Color(150, 220, 100)),
	PROTECTED("Protected", "Hidden", new Color(100,80,0),new Color(200, 200, 100)),
	PRIVATE("Private", "Not searchable", new Color(160,0,0),new Color(220, 150, 100));


	private final String name;
	private final String comments;
	private final Color fgColor;
	private final Color bgColor;

	private Privacy(String name, String comments, Color fgColor, Color bgColor) {
		this.name = name;
		this.comments = comments;
		this.fgColor = fgColor;
		this.bgColor = bgColor;
	}

	public String getName() {
		return name;
	}

	public Color getFgColor() {
		return fgColor;
	}

	public Color getBgColor() {
		return bgColor;
	}

	/**
	 * @return the comments
	 */
	public String getComments() {
		return comments;
	}

	@Override
	public String toString() {
		return name;
	}

	public static Privacy get(String toStringRepresentation) {
		if(toStringRepresentation==null) return null;
		for (Privacy l : values()) {
			if(l.getName().equalsIgnoreCase(toStringRepresentation)) return l;
		}
		return null;
	}

}
