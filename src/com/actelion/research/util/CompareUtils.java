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

package com.actelion.research.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;




@SuppressWarnings({"unchecked", "rawtypes"})
public class CompareUtils {


	public static <T> boolean contains(T[] array, T object) {
		for (T t : array) {
			if(t==object) return true;
		}
		return false;
	}


	public static final int compare(String s1, String s2) {
		if(s1==null && s2==null) return 0;
		if(s1==null) return 1;
		if(s2==null) return -1;

		long l1 = fastLongValueOf(s1);
		long l2 = fastLongValueOf(s2);
		if(l1>l2) return 1;
		if(l1<l2) return -1;
		return s1.compareToIgnoreCase(s2);

		//		boolean allDigits1 = true;
		//		boolean allDigits2 = true;
		//		for(int j=0; allDigits1 && j<s1.length();j++) {
		//			if(!Character.isDigit(s1.charAt(j))) allDigits1 = false;
		//		}
		//		for(int j=0; allDigits2 && j<s2.length();j++) {
		//			if(!Character.isDigit(s2.charAt(j))) allDigits2 = false;
		//		}
		//		if(allDigits1 && allDigits2) {
		//			long l1 = fastLongValueOf(s1);
		//			long l2 = fastLongValueOf(s2);
		//			return l1>l2?1: l1==l2? 0: -1;
		//		} else if(allDigits1 && !allDigits2) {
		//			return -1;
		//		} else if(!allDigits1 && allDigits2) {
		//			return 1;
		//		} else {
		//			//Compare by string first case insensitive
		//			return s1.compareToIgnoreCase(s2);
		//		}
	}

	/**
	 * Compare 2 strings by splitting the chains into blocks and comparing each blocks individually.
	 * Each Block is then compared as integer or string.
	 * Special character and null go to the end
	 * Example
	 * Dose 10 ->  [Dose] [10]
	 * Dose 100 -> [Dose] [100]
	 *
	 * Caveat:
	 * The sorting considers "-" as negative only if it is the first character, otherwise it is considered as a separator, so the sorting goes:
	 * -10, -2, -1, _1, 1, 2, 10, A, B, C, -, null
	 *
	 * @param o1
	 * @param o2
	 * @return
	 */
	public static final int compareSpecial(String o1, String o2) {
		if(o1==null && o2==null) return 0;
		if(o1==null) return 1;
		if(o2==null) return -1;


		if(o1.startsWith("-") && !o2.startsWith("-")) {
			return -1;
		} else if(!o1.startsWith("-") && o2.startsWith("-")) {
			return 1;
		}
		final String separators = "_/,;-.:\n\t+ ";
		StringTokenizer st1 = new StringTokenizer(o1, separators, true);
		StringTokenizer st2 = new StringTokenizer(o2, separators, true);

		String s1, s2;
		boolean allDigits1, allDigits2;
		int c;
		while(st1.hasMoreTokens() && st2.hasMoreTokens()) {
			s1 = st1.nextToken();
			s2 = st2.nextToken();

			if(s1.length()==0) {
				if(s2.length()==0) continue;
				else return -1;
			} else if(s2.length()==0) {
				return 1;
			}

			if(s1.length()==1 && separators.indexOf(s1.charAt(0))>=0) {
				if(s2.length()==1 && separators.indexOf(s2.charAt(0))>=0) {
					c = separators.indexOf(s1.charAt(0))- separators.indexOf(s2.charAt(0));
					if(c!=0) return c;
				} else {
					return 1;
				}
			} else if(s2.length()==1 && !Character.isLetterOrDigit(s2.charAt(0))) {
				return -1;
			}


			//Compare first the numeric value if possible
			allDigits1 = true;
			allDigits2 = true;
			for(int j=0; allDigits1 && j<s1.length();j++) {
				if(!Character.isDigit(s1.charAt(j))) allDigits1 = false;
			}
			for(int j=0; allDigits2 && j<s2.length();j++) {
				if(!Character.isDigit(s2.charAt(j))) allDigits2 = false;
			}
			if(allDigits1 && allDigits2) {
				long l1 = fastLongValueOf(s1);
				long l2 = fastLongValueOf(s2);
				c =  l1>l2?1: l1==l2? 0: -1;
				if(c!=0) return c;
			} else if(allDigits1 && !allDigits2) {
				return -1;
			} else if(!allDigits1 && allDigits2) {
				return 1;
			} else {
				//Compare by string first case insensitive
				c = s1.compareToIgnoreCase(s2);
				if(c!=0) return c;
			}

		}

		if(!st1.hasMoreTokens() && st2.hasMoreTokens()) return -1;
		if(st1.hasMoreTokens() && !st2.hasMoreTokens()) return 1;

		return o1.compareTo(o2);
	}

	public static long fastLongValueOf(String str ) {
		long ival = 0;
		for(int i=0; i<str.length(); i++) {
			if(str.charAt(i)<'0' || str.charAt(i)>'9') break;
			ival = ival*10 + (str.charAt(i)-'0');
		}
		return ival;
	}

	public static int compare(Object[] a1, Object[] a2) {
		for (int i = 0; i < a1.length || i < a2.length; i++) {
			Object o1 = i < a1.length? a1[i]: null;
			Object o2 = i < a2.length? a2[i]: null;
			int c = CompareUtils.compare(o1, o2);
			if(c!=0) return c;
		}
		return 0;
	}

	public static int compare(int[] a1, int[] a2) {
		if(a1.length!=a2.length) return a1.length-a2.length;
		for (int i = 0; i < a1.length; i++) {
			int c = a1[i] - a2[i];
			if(c!=0) return c;
		}
		return 0;
	}

	public static int compare(Object o1, Object o2) {
		if(o1==null && o2==null) return 0;
		if(o1==null) return 1; //Null at the end
		if(o2==null) return -1;

		if((o1 instanceof String) && (o2 instanceof String)) {
			return compare((String) o1, (String) o2);
		} else if((o1 instanceof Object[]) && (o2 instanceof Object[])) {
			return compare((Object[]) o1, (Object[]) o2);
		} else if((o1 instanceof Comparable) && o2.getClass().isAssignableFrom(o1.getClass())) {
			return ((Comparable) o1).compareTo(o2);
		} else if((o1 instanceof Comparable) && o1.getClass().isAssignableFrom(o2.getClass())) {
			return -((Comparable) o2).compareTo(o1);
		} else {
			return compare(o1.toString(), o2.toString());
		}
	}

	public static final int compareAsDate(Object o1, Object o2) {
		if(o1==null && o2==null) return 0;
		if(o1==null) return 1; //Null at the end
		if(o2==null) return -1;

		Date d1 = FormatterUtils.parseDateTime(o1.toString());
		Date d2 = FormatterUtils.parseDateTime(o2.toString());
		if(d1!=null && d2!=null) {
			return d1.compareTo(d2);
		} else if(d1==null && d2!=null) {
			return 1;
		} else if(d1!=null && d2==null) {
			return -1;
		} else {
			return compare(o1, o2);
		}
	}


	/**
	 * Comparator to allow null values
	 */
	public static final Comparator<Object> OBJECT_COMPARATOR = new Comparator<Object>() {
		@Override
		public int compare(Object o1, Object o2) {
			if(o1==null && o2==null) return 0;
			if(o1==null) return 1; //Null at the end
			if(o2==null) return -1;
			return CompareUtils.compare(o1, o2);
		}
	};



	/**
	 * Comparator for strings, comparing blocks separately so that the order becomes SLIDE-1, SLIDE-2, SLIDE-10, SLIDE-10-1, ...
	 */
	public static final Comparator<Object> STRING_COMPARATOR = new Comparator<Object>() {
		@Override
		public int compare(Object o1, Object o2) {
			if(o1==null && o2==null) return 0;
			if(o1==null) return 1; //Null at the end
			if(o2==null) return -1;
			return CompareUtils.compare(o1.toString(), o2.toString());
		}
	};

	/**
	 * Comparator for dates, allowing formats such as yyyy, mm.yyyy, ...
	 */
	public static final Comparator<Object> DATE_COMPARATOR = new Comparator<Object>() {
		@Override
		public int compare(Object o1, Object o2) {
			return CompareUtils.compareAsDate(o1, o2);
		}
	};

	public static boolean equals(Object obj1, Object obj2) {
		return obj1==null? obj2==null: obj1.equals(obj2);
	}

	public static boolean equals(String s1, String s2) {
		return (s1==null?"":s1).equals((s2==null?"":s2));
	}

	/**
	 * Test Speed
	 * @param args
	 */
	public static void main(String[] args) {
		//		List<String> initial = Arrays.asList(new String[] {"heart", "", "lung", "lung/left", "1", "10", "2", "3", "11", "Box1","Box10", "Box2", "Box 2", "Box 1", "Box 10", "10.9.2012", "11.9.12", "2012", "Genomics", "_2", "_3", "_10", " 1", "_1", "-10", "-1. 0", "-2", "-1.00", "-1. 00", "-1.  00", "1-1", "1-10", "Proteomics", "Clinical Analysis", "Lung/Right", "Lung", "Lung/Left", "1", "-1", "-1.1", "-1.10", "2.A", "10.B-1", "10.B-10", "10.B-2", "1.C", "21.D", "3.B-3", "Heart","11.C", "11. C", "10. B-1", "2.C", "1.B","d030; Heart","d030; Heart/Left ventricle + Septum","d030; Heart/Right ventricle"});
		//		List<String> initial = Arrays.asList(new String[] {"-5_4", "-5", "+1_8", "+2", "-", "#", "1", "2B", "2A", "3A", "3B", "a", "A", "b", "B", "c", "C", "1", "11", "2", "21", "22"});
		List<String> initial = Arrays.asList(new String[] {"-10", "-2", "-1", "_1", "1", "2", "10", "A", "B", "C", "-", "null"});
		List<String> l = new ArrayList<String>();
		l.addAll(initial);
		//		l.addAll(initial);
		//		l.addAll(initial);
		//		l.addAll(initial);
		//		l.addAll(initial);
		//		l.addAll(initial);
		//		l.addAll(initial);
		//		l.addAll(initial);


		List<String> l2 = new ArrayList<String>(l);
		long st = System.currentTimeMillis();
		Collections.sort(l2);
		long t1 = System.currentTimeMillis()-st;

		List<String> l3 = new ArrayList<String>(l);
		st = System.currentTimeMillis();
		Collections.sort(l3, DATE_COMPARATOR);
		long t2 = System.currentTimeMillis()-st;

		st = System.currentTimeMillis();
		Collections.sort(l, STRING_COMPARATOR);
		long t3 = System.currentTimeMillis()-st;

		for (String string : l) {
			System.out.println(string);
		}

		System.out.println();
		System.out.println("CompareUtils.normal: "+t1);
		System.out.println("CompareUtils.date: "+t2);
		System.out.println("CompareUtils.string: "+t3);
	}



}
