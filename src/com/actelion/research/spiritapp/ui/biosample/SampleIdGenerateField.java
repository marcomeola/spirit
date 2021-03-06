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

import java.awt.Dimension;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.border.Border;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

import com.actelion.research.spiritcore.business.biosample.Biosample;
import com.actelion.research.spiritcore.business.study.AttachedBiosample;
import com.actelion.research.spiritcore.services.dao.DAOBarcode;
import com.actelion.research.util.ui.FastFont;
import com.actelion.research.util.ui.JCustomTextField;

/**
 * TextField for Ids.
 * This textfield can automatically generate sampleIds based on the given prefix.
 *
 * The generate button will react like:
 * - If the initialized underlying object (Biosample for example) was not initialized with a sampleId, this will create a new unique id (through DAOBarcode) based on the prefix
 * - If the initialized underlying object was initialized with a sampleId, this will return the same given sampleId (not avoid creating a new one)
 *
 * The function scanTextField.initUnderlyingObject must always be called before use
 * <pre>
 * scanTextField = new SampleIdGenerateField<Biosample>();
 * scanTextField.initUnderlyingObject(biosample, biotype.getPrefix(), biosample.getSampleId());
 * </pre>
 *
 *
 * @author freyssj
 *
 * @param <UNDERLYING>
 */
public class SampleIdGenerateField<UNDERLYING> extends JCustomTextField {

	private String currentPrefix;
	private JButton generateButton = new JButton("Gen");

	/**HashMap used to map the prefix to the map of biosample to the next sampleid */
	private Map<String, IdentityHashMap<UNDERLYING, String>> prefix2map = new HashMap<>();
	private UNDERLYING currentObject;

	public SampleIdGenerateField() {
		super(10, "", "SampleId");

		generateButton.setBorder(BorderFactory.createEmptyBorder());
		generateButton.addActionListener(e-> {
			if(!SampleIdGenerateField.this.isEnabled()) return;
			String id = generateSampleIdFor(currentObject, currentPrefix);
			setText(id);
		});

		setLayout(null);
		generateButton.setBorder(null);
		setToolTipText("Click here and scan an ID or click GET to generate a new sampleId");
		generateButton.setFont(FastFont.SMALLER);
		generateButton.setToolTipText("Generate a new sampleId");
		add(generateButton);

		setDocument(new PlainDocument() {
			@Override
			public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
				super.insertString(offs, str, a);
				addSuffix();
			}

			private void addSuffix() {
				try {
					String prefix = getText(0, getCaretPosition());
					if(prefix.length()<2) return;
					for (int i = 0; i < prefix.length(); i++) {
						if(Character.isDigit(prefix.charAt(i))) return;
					}

					//Find the suffix
					Biosample b = (currentObject instanceof Biosample)? ((Biosample)currentObject): (currentObject instanceof AttachedBiosample)? ((AttachedBiosample)currentObject).getBiosample(): null;
					String suffix = DAOBarcode.getNextId(b);
					if(suffix.length()<prefix.length()) return;
					suffix = suffix.substring(prefix.length());

					//Insert the suffix
					int caret = getCaretPosition();
					super.insertString(caret, suffix, null);

					//select the suffix
					setCaretPosition(getLength());
					moveCaretPosition(caret);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public Dimension getPreferredSize() {
		Dimension dim = super.getPreferredSize();
		return new Dimension(dim.width+25, dim.height);
	}

	/**
	 * Utility class used to generate sampleIds without setting the underlying object and sampleId
	 * @param object
	 * @param useCacheIfPossible
	 * @return
	 */
	public String generateSampleIdFor(UNDERLYING object, String prefix) {

		if(object==null) return null;
		if(prefix==null) return null;

		//retrieve from cache or retrieve a new one
		IdentityHashMap<UNDERLYING, String> object2sampleId = prefix2map.get(prefix);
		if(object2sampleId==null) {
			prefix2map.put(prefix, object2sampleId = new IdentityHashMap<>());
		}

		String memo = object2sampleId.get(object);
		if(memo!=null) return memo;

		//Generate a new barcode
		try {
			Biosample b = (object instanceof Biosample)? ((Biosample)object): (object instanceof AttachedBiosample)? ((AttachedBiosample)object).getBiosample(): null;
			String id = DAOBarcode.getNextId(b);
			object2sampleId.put(object, id);
			return id;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Memorizes the nextId, and set the biotype
	 * @param object
	 * @param prefix (null or empty will desactivate the generate button)
	 * @param cachedId (null, to set the prefix to be generated)
	 */
	public void putCachedSampleId(UNDERLYING object, String prefix, String cachedId) {
		generateButton.setEnabled(isEnabled() && prefix!=null && prefix.length()>0);
		if(object==null) return;

		IdentityHashMap<UNDERLYING, String> object2sampleId = prefix2map.get(prefix);
		if(object2sampleId==null) {
			prefix2map.put(prefix, object2sampleId = new IdentityHashMap<>());
		}

		//Memorizes the previously generated sampleId
		if(object!=null && cachedId!=null && cachedId.length()>0 && object2sampleId.get(object)==null) {
			object2sampleId.put(object, cachedId);
		}

		this.currentPrefix = prefix;
		this.currentObject = object;
	}

	@Override
	public void doLayout() {
		generateButton.setBounds(getWidth()-23, 1, 23, getHeight()-2);
	}

	public String getSampleId() {
		String t = getText();
		return t;
	}

	@Override
	public void setEnabled(boolean enabled) {
		generateButton.setEnabled(enabled && currentPrefix!=null && currentPrefix.length()>0);
		super.setEnabled(enabled);
	}

	@Override
	public void setBorder(Border border) {
		if(border==null) return;
		super.setBorder(BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(0, 0, 0, 12)));
	}

}
