/**
 * Copyright (c) 2015, Model-Based Systems Engineering Center, Georgia Institute of Technology.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 * 
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 * 
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *   
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package edu.gatech.mbse.plugins.papyrus.sysml2milp.handlers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.uml2.uml.Activity;
import org.eclipse.uml2.uml.NamedElement;

import edu.gatech.mbse.transformations.sysml2milp.MILP2SysMLTransformation;
import edu.gatech.mbse.transformations.sysml2milp.SysML2MILPTransformation;
import matlabcontrol.MatlabConnectionException;
import matlabcontrol.MatlabInvocationException;
import matlabcontrol.MatlabProxy;
import matlabcontrol.MatlabProxyFactory;
import matlabcontrol.MatlabProxyFactoryOptions;
import matlabcontrol.MatlabProxyFactoryOptions.Builder;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.papyrus.infra.core.resource.NotFoundException;
import org.eclipse.papyrus.uml.tools.model.UmlUtils;

/**
 * Activator for SysML2MILP transformation plugin for Papyrus.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class TransformAndExecuteHandler extends AbstractHandler {
	
	/**
	 * The constructor.
	 */
	public TransformAndExecuteHandler() {
	}

	/**
	 * the command has been executed, so extract extract the needed information
	 * from the application context.
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// Get selected elements
		List<NamedElement> selectedObjects = getSelectedUmlObjects();
		
		if (selectedObjects != null) {
			if (selectedObjects.get(0) instanceof Activity) {
				final Activity process = (Activity) selectedObjects.get(0);
				NamedElement rootElementI = null;
				
				try {
					rootElementI = (NamedElement) UmlUtils.getUmlModel().lookupRoot();
				} catch (NotFoundException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				};
				
				final NamedElement rootElement = rootElementI;
	
				Job job = new Job("SysML2MILP Transformation & Execution") {
					
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						// Now transform
						SysML2MILPTransformation trafo = new SysML2MILPTransformation();
						
						monitor.worked(15);
						
						String milpCode;
						
						try {
							milpCode = trafo.transform((Activity) process, rootElement, 1);
						
							monitor.worked(20);
							
							// Still store the generated matlab script
							try {
								File f = new File(System.getProperty("user.home") + "\\milp_generated.m");
								
								FileWriter fw = new FileWriter(f);
								BufferedWriter bw = new BufferedWriter(fw);
								
								bw.write(milpCode);
								bw.close();
								//JOptionPane.showMessageDialog(null, "SysML <-> MILP transformation done! Output is stored in home\ndirectory. You can now execute the generated Matlab script.", "SysML <-> MILP Transformation Plugin", JOptionPane.INFORMATION_MESSAGE);
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
							
							// Execute Matlab script
							// NOTE It seems that matlabcontrol must be on a path without spaces - otherwise it won't work
							executeMatlabScript(milpCode);
							
							monitor.worked(80);
							
							// Do back transformation
							MILP2SysMLTransformation backTransformation = new MILP2SysMLTransformation();
							backTransformation.transform(rootElement, (Activity) process);
							
							return Status.OK_STATUS;
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							
							return Status.CANCEL_STATUS;
						}
					}
					
				};
				
				// Start job
				job.schedule();
			}
		}
		return null;
	}
	
	/**
	 * Returns the selected elements.
	 * <p>
	 * Credit to https://wiki.eclipse.org/Papyrus_Developer_Guide/How_To_Code_Examples#Core_Examples
	 * 
	 * @return
	 */
	protected List<Object> lookupSelectedElements() {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		ISelection selection = page.getSelection();
		
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection structuredSelection = (IStructuredSelection) selection;
			return structuredSelection.toList();
		} else if (selection instanceof TreeSelection) {
			TreeSelection treeSelection = (TreeSelection) selection;
			return treeSelection.toList();
		}
		return null;
	}
	
	/**
	 * Return a list of selected domain (UML) elements.
	 * <p>
	 * Credit to https://wiki.eclipse.org/Papyrus_Developer_Guide/How_To_Code_Examples#Core_Examples
	 * 
	 * @return
	 */
	protected List<NamedElement> getSelectedUmlObjects() {
		List<Object> selections = lookupSelectedElements(); // see "How to Get
															// the Current
															// Selection from
															// Java code"

		List<NamedElement> results = new ArrayList<NamedElement>();

		// create model with EList<EObject> objects
		for (Object obj : selections) {
			// Adapt object to NamedElement
			NamedElement ele = null;
			if (obj instanceof IAdaptable) {
				ele = (NamedElement) ((IAdaptable) obj).getAdapter(NamedElement.class);
			}
			if (ele == null) {
				ele = (NamedElement) Platform.getAdapterManager().getAdapter(obj, NamedElement.class);
			}
			if (ele != null) {
				results.add(ele);
			}
		}
		return results;
	}
	
	/**
	 * Execute a Matlab script.
	 * 
	 * @param code A string representation of the matlab code.
	 */
	protected void executeMatlabScript(String code) {
		Builder optionsBuilder = new Builder();
		
		// Default options
		optionsBuilder = optionsBuilder.setHidden(true);
		optionsBuilder = optionsBuilder.setUsePreviouslyControlledSession(true);
				
		MatlabProxyFactoryOptions options = optionsBuilder.build();
		MatlabProxyFactory factory = new MatlabProxyFactory(options);
		MatlabProxy proxy = null;
		
		try {
			proxy = factory.getProxy();
		} catch (MatlabConnectionException e) {
			e.printStackTrace();
			
			return;
		}
		
		try {
			// Now execute MILP code
			proxy.eval(code);
		} catch (MatlabInvocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
