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

package com.actelion.research.spiritcore.business.pivot;

import java.util.List;

import com.actelion.research.spiritcore.business.result.Result;

/**
 * The CompactPivotTemplate is used to limit the number of columns by having maximum 1 discriminant column
 * @author Joel Freyss
 *
 */
public class CompactPivotTemplate extends PivotTemplate {

	public CompactPivotTemplate() {
		super("Compact", "compact.png");
	}

	@Override
	public void init(List<Result> results) {
		clear();
		setWhere(PivotItemFactory.STUDY_STUDYID, Where.ASROW);
		setWhere(PivotItemFactory.STUDY_GROUP, Where.ASROW);
		setWhere(PivotItemFactory.STUDY_SUBGROUP, Where.ASROW);
		setWhere(PivotItemFactory.BIOSAMPLE_TOPID, Where.ASROW);
		setWhere(PivotItemFactory.BIOSAMPLE_TOPNAME, Where.ASROW);
		setWhere(PivotItemFactory.RESULT_TEST, Where.ASCOL);
		setWhere(PivotItemFactory.RESULT_OUTPUT, Where.ASCOL);

		if(isDiscriminating(PivotItemFactory.BIOSAMPLE_NAME, results)) {
			setWhere(PivotItemFactory.BIOSAMPLE_NAME, !isMultiColumns(results) && !hasMoreOrEqualThanNValues(PivotItemFactory.BIOSAMPLE_NAME, results, 25)? Where.ASCOL: Where.ASCELL);
			setWhere(PivotItemFactory.RESULT_INPUT, !isMultiColumns(results) && !hasMoreOrEqualThanNValues(PivotItemFactory.RESULT_INPUT, results, 25)? Where.ASCOL: Where.ASCELL);
		} else {
			setWhere(PivotItemFactory.BIOSAMPLE_NAME, Where.ASCOL);
			setWhere(PivotItemFactory.RESULT_INPUT, !isMultiColumns(results) && !hasMoreOrEqualThanNValues(PivotItemFactory.RESULT_INPUT, results, 25)? Where.ASCOL: Where.ASCELL);
		}
		if(isDiscriminating(PivotItemFactory.STUDY_PHASE_DATE, results)) {
			setWhere(PivotItemFactory.STUDY_PHASE_DATE, !isMultiColumns(results)? Where.ASCOL: Where.ASCELL);
		}

		if(isDiscriminating(PivotItemFactory.BIOSAMPLE_METADATA, results)) {
			setWhere(PivotItemFactory.BIOSAMPLE_METADATA, Where.ASCELL);
		}

		if(isDiscriminating(PivotItemFactory.BIOSAMPLE_COMMENTS, results)) {
			setWhere(PivotItemFactory.BIOSAMPLE_COMMENTS, Where.ASCELL);
		}
		if(isDiscriminating(PivotItemFactory.RESULT_COMMENTS, results)) {
			setWhere(PivotItemFactory.RESULT_COMMENTS, Where.ASCELL);
		}

		setAggregation(Aggregation.ALL_VALUES);
		simplify(results);
	}

}
