/***************************************************************************
 *
 * This file is part of the NFC Eclipse Plugin project at
 * http://code.google.com/p/nfc-eclipse-plugin/
 *
 * Copyright (C) 2012 by Thomas Rorvik Skjolberg.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 ****************************************************************************/

package org.nfc.eclipse.plugin.model.editing;

import java.util.Collection;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.nfc.eclipse.plugin.model.NdefRecordModelBinaryProperty;
import org.nfc.eclipse.plugin.model.NdefRecordModelFactory;
import org.nfc.eclipse.plugin.model.NdefRecordModelNode;
import org.nfc.eclipse.plugin.model.NdefRecordModelProperty;
import org.nfc.eclipse.plugin.operation.DefaultNdefModelPropertyOperation;
import org.nfc.eclipse.plugin.operation.NdefModelOperation;
import org.nfc.eclipse.plugin.operation.NdefModelOperationList;
import org.nfc.eclipse.plugin.util.FileDialogUtil;
import org.nfctools.ndef.mime.BinaryMimeRecord;
import org.nfctools.ndef.mime.MimeRecord;

import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.detector.ExtensionMimeDetector;

public class MimeRecordEditingSupport extends DefaultRecordEditingSupport {

	public static NdefModelOperation newSetContentOperation(BinaryMimeRecord binaryMimeRecord, NdefRecordModelProperty node, byte[] next) {
		return new SetContentOperation(binaryMimeRecord, (NdefRecordModelProperty)node, binaryMimeRecord.getContent(), next);
	}

	private static class SetContentOperation extends DefaultNdefModelPropertyOperation<byte[], BinaryMimeRecord> {

		public SetContentOperation(BinaryMimeRecord record, NdefRecordModelProperty ndefRecordModelProperty, byte[] previous, byte[] next) {
			super(record, ndefRecordModelProperty, previous, next);
		}

		@Override
		public void execute() {
			super.execute();
			
			record.setContent(next);
			
			if(next == null) {
				ndefRecordModelProperty.setValue(NdefRecordModelFactory.getNoBytesString());
			} else {
				ndefRecordModelProperty.setValue(NdefRecordModelFactory.getBytesString(next.length));
			}	

		}
		
		@Override
		public void revoke() {
			super.revoke();
			
			record.setContent(previous);
			
			if(previous == null) {
				ndefRecordModelProperty.setValue(NdefRecordModelFactory.getNoBytesString());
			} else {
				ndefRecordModelProperty.setValue(NdefRecordModelFactory.getBytesString(previous.length));
			}	
		}
	}
	
	public MimeRecordEditingSupport(
			TreeViewer treeViewer) {
		super(treeViewer);
	}

	@Override
	public NdefModelOperation setValue(NdefRecordModelNode node, Object value) {
		MimeRecord mimeRecord = (MimeRecord) node.getRecord();
		if(node instanceof NdefRecordModelProperty) {
			String stringValue = (String)value;
			
			int parentIndex = node.getParentIndex();
			if(parentIndex == 0) {
				FileDialogUtil.registerMimeType(stringValue);
				
				if(!stringValue.equals(mimeRecord.getContentType())) {
					return new DefaultNdefModelPropertyOperation<String, MimeRecord>(mimeRecord, (NdefRecordModelProperty)node, mimeRecord.getContentType(), stringValue) {
						
						@Override
						public void execute() {
							super.execute();
							
							record.setContentType(next);
						}
						
						@Override
						public void revoke() {
							super.revoke();
							
							record.setContentType(previous);
						}
					};
				}
			} else if(parentIndex == 1) {
				if(mimeRecord instanceof BinaryMimeRecord) {
					BinaryMimeRecord binaryMimeRecord = (BinaryMimeRecord)mimeRecord;
					if(value != null) {
					
						byte[] payload = load((String)value);
	
						if(payload != null) {
							NdefRecordModelBinaryProperty ndefRecordModelBinaryProperty = (NdefRecordModelBinaryProperty)node;
							ndefRecordModelBinaryProperty.setFile((String)value);
							
							NdefModelOperation contentOperation = newSetContentOperation(binaryMimeRecord, (NdefRecordModelProperty)node, payload);

							// can we auto-detect the mime type?
							String contentType = binaryMimeRecord.getContentType();
							if(contentType == null || contentType.length() == 0) {
								
								ExtensionMimeDetector extensionMimeDetector = new ExtensionMimeDetector();								
								Collection<MimeType> mimeTypes = extensionMimeDetector.getMimeTypes((String)value);
								if(!mimeTypes.isEmpty()) {
									
									MimeType mimeType = (MimeType) mimeTypes.iterator().next();
									
									NdefModelOperation mimeTypeOperation = new DefaultNdefModelPropertyOperation<String, MimeRecord>(mimeRecord, (NdefRecordModelProperty)node.getParent().getChild(0), mimeRecord.getContentType(), mimeType.toString()) {
											
											@Override
											public void execute() {
												super.execute();
												
												record.setContentType(next);
											}
											
											@Override
											public void revoke() {
												super.revoke();
												
												record.setContentType(previous);
											}
										};
										
									NdefModelOperationList listOperation = new NdefModelOperationList();
									listOperation.add(mimeTypeOperation);
									listOperation.add(contentOperation);
									
									return listOperation;
								}
							}
						
							return contentOperation;
						}
					}
				} else {
					throw new RuntimeException();
				}
			}
			
			return null;
		} else {
			return super.setValue(node, value);
		}
	}

	@Override
	public Object getValue(NdefRecordModelNode node) {
		MimeRecord mimeRecord = (MimeRecord) node.getRecord();
		if(node instanceof NdefRecordModelProperty) {
			int parentIndex = node.getParentIndex();
			if(parentIndex == 0) {
				return mimeRecord.getContentType();
			} else if(parentIndex == 1) {
				return EMPTY_STRING;
			} else {
				throw new RuntimeException();
			}
		} else {
			return super.getValue(node);
		}
	}

	@Override
	public CellEditor getCellEditor(NdefRecordModelNode node) {
		if(node instanceof NdefRecordModelProperty) {
			int parentIndex = node.getParentIndex();
			if(parentIndex == 0) {
				return new TextCellEditor(treeViewer.getTree());
			} else if(parentIndex == 1) {
				return new FileDialogCellEditor(treeViewer.getTree());
			} else {
				throw new RuntimeException();
			}
		} else {
			return super.getCellEditor(node);
		}
	}
}