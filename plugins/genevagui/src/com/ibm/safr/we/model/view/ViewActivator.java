package com.ibm.safr.we.model.view;

/*
 * Copyright Contributors to the GenevaERS Project. SPDX-License-Identifier: Apache-2.0 (c) Copyright IBM Corporation 2008.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.genevaers.runcontrolgenerator.workbenchinterface.WorkbenchCompiler;
import org.genevaers.runcontrolgenerator.workbenchinterface.FormatFilterSyntaxChecker;
import org.genevaers.runcontrolgenerator.workbenchinterface.WBCompilerType;
import org.genevaers.runcontrolgenerator.workbenchinterface.WBExtractColumnCompiler;
import org.genevaers.runcontrolgenerator.workbenchinterface.WBExtractFilterCompiler;
import org.genevaers.runcontrolgenerator.workbenchinterface.WBExtractOutputCompiler;
import org.genevaers.runcontrolgenerator.workbenchinterface.WBCompilerFactory;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchPartSite;


import com.ibm.safr.we.SAFRUtilities;
import com.ibm.safr.we.constants.CodeCategories;
import com.ibm.safr.we.constants.Codes;
import com.ibm.safr.we.constants.OutputFormat;
import com.ibm.safr.we.constants.ReportType;
import com.ibm.safr.we.constants.SAFRCompilerErrorType;
import com.ibm.safr.we.constants.SAFRPersistence;
import com.ibm.safr.we.data.DAOException;
import com.ibm.safr.we.data.DAOFactoryHolder;
import com.ibm.safr.we.data.WECompilerDataProvider;
import com.ibm.safr.we.exceptions.SAFRException;
import com.ibm.safr.we.exceptions.SAFRValidationException;
import com.ibm.safr.we.exceptions.SAFRViewActivationException;
import com.ibm.safr.we.model.SAFRApplication;
import com.ibm.safr.we.model.SAFRValidator;
import com.ibm.safr.we.preferences.SAFRPreferences;
import com.ibm.safr.we.ui.reports.ReportUtils;
import com.ibm.safr.we.utilities.SAFRLogger;

public class ViewActivator {    
      
    static transient Logger logger = Logger.getLogger("com.ibm.safr.we.model.view.ViewActivator");
    
    private View view;
    private ArrayList<ViewLogicDependency> viewLogicDependencies;
    private SAFRViewActivationException vaException = new SAFRViewActivationException(view);

	private Set<Integer> CTCols;
	private static IWorkbenchPartSite siteRef;

    public ViewActivator(View view) {
        super();
        this.view = view;
    }
    
    protected SAFRViewActivationException getVaException() {
        return vaException;
    }    
    
    static public String getNewCompilerVersion() {
        return WorkbenchCompiler.getVersion();          
    }
    
    static public String getCompilerVersion() {
            return getNewCompilerVersion();
    }

    static public String getAllCompilerVersion() {
        return getNewCompilerVersion();
    }
    
    
	public static void setSite(IWorkbenchPartSite site) {
		siteRef = site;
	}
    /**
     * Invoke activation 
     * 
     * @throws DAOException, SAFRException
     */ 
    public void activate() throws DAOException, SAFRException {
        SAFRLogger.logAllSeparator(logger, Level.INFO, "Activating View " + view.getDescriptor());
//		MessageDialog.openError(siteRef.getShell(),
//				wf.greetings(),
//                "Acivate");
        
        vaException = new SAFRViewActivationException(view);   
        try {
            activateNew();
        } catch (SAFRViewActivationException e) {
        	//Don't log the exception trace
            logger.log(Level.INFO, "New Activation Exception");
        }
        
        view.setCompilerVersion(ViewActivator.getCompilerVersion());
        
        if (vaException.hasErrorOrWarningOccured()) {
            if (vaException.hasErrorOccured()) {
                SAFRLogger.logAll(logger, Level.INFO, "Failed Activation");                
                logActivationErrors();                
            } else {
                SAFRLogger.logAll(logger, Level.INFO, "Successful Activation with warnings");
                logActivationErrors();
            }
            SAFRLogger.logEnd(logger);
            throw vaException;
        } else {
            SAFRLogger.logAll(logger, Level.INFO, "Successful Activation");
            SAFRLogger.logEnd(logger);
        }
    }

    protected void logActivationErrors() {
        if (vaException.getActivationLogNew().size() > 0) {     
            logActErrors("Errors/Warnings from Compiler", vaException.getActivationLogNew());
        }
    }

    protected void logActErrors(String title, List<ViewActivationError> rawErrors) {
        String strErrors = title + System.lineSeparator();
        Map<SAFRCompilerErrorType, List<ViewActivationError>> errorMap = 
            new HashMap<SAFRCompilerErrorType, List<ViewActivationError>>();
        for (ViewActivationError error : rawErrors) {
            if (errorMap.containsKey(error.getErrorType())) {
                errorMap.get(error.getErrorType()).add(error);
            } else {
                List<ViewActivationError> errors = new ArrayList<ViewActivationError>();
                errors.add(error);
                errorMap.put(error.getErrorType(), errors);
            }
        }
        for (SAFRCompilerErrorType type : errorMap.keySet()) {
            strErrors += type.getText() + " Activation Errors and Warnings" + System.lineSeparator();
            strErrors += String.format("%s, %s, %s", "Message", "Column", "Source") + System.lineSeparator();
            for (ViewActivationError error : errorMap.get(type)) {
                strErrors += getErrorLine(error) + SAFRUtilities.LINEBREAK;
            }
            strErrors = strErrors.substring(0, strErrors.length() - SAFRUtilities.LINEBREAK.length());
        }
        SAFRLogger.logAll(logger, Level.INFO, strErrors);
    }
    
    private String getErrorLine(ViewActivationError error) {
        return String.format("%s, %s, %s", 
            error.getErrorText() == null ? "" : error.getErrorText().replaceAll("(?:\\n|\\r)", ""), 
            error.viewColumn == null ? "" :  error.viewColumn.getDescriptor(), 
            error.viewSource == null ? "" :  error.viewSource.getDescriptor());
    }
    
    /**
     * Activate
     * 
     * @throws DAOException, SAFRException
     */ 
    protected void activateNew() throws DAOException, SAFRException {

        initialization();                

        compileFormatCalculation();
        compileViewSources();
        compileFormatFilter();
                
        processResult();        
    }

    protected void initialization() throws SAFRException, DAOException,
        SAFRViewActivationException {
        checkActivationShowStoppers();        
        initViewDependencies();
        initializeViewColumns();
    }

    protected void checkActivationShowStoppers()
        throws SAFRException, DAOException, SAFRViewActivationException {
        checkSavedView();
        checkFormatHasSortKey();
        checkHasViewSources();
        checkConsistentHardcopy();        
        checkDuplicateViewSource();        
        checkSortKeyTitles();
        checkRecordAggregation();
        checkCopyViewHasNoColumns();
        checkNormalViewHasColumns();
        if (!vaException.getActivationLogNew().isEmpty()) {
            throw vaException; // cannot continue.                
        }
    }

    private void checkNormalViewHasColumns() {
        if (! view.getOutputFormat().equals(OutputFormat.Extract_Source_Record_Layout)) {
        	if(view.getViewColumns().size() == 0) {
                vaException.addActivationError(new ViewActivationError(null, null,
                        SAFRCompilerErrorType.VIEW_PROPERTIES,
                        "Only a Source Record Layout view can have no columns."));
        	}
        }		
	}

	private void checkCopyViewHasNoColumns() {
        if (view.getOutputFormat().equals(OutputFormat.Extract_Source_Record_Layout)) {
        	if(view.getViewColumns().size() > 0) {
                vaException.addActivationError(new ViewActivationError(null, null,
                        SAFRCompilerErrorType.VIEW_PROPERTIES,
                        "A Source Record Layout view cannot have columns."));
        	}
        }		
	}

	private void checkRecordAggregation() {
        if (view.isFormatPhaseInUse() && view.isFormatPhaseRecordAggregationOn()) {
        	for(ViewColumn c : view.getViewColumns()) {
        		if(c.isSortKey() == false && c.isNumeric() && c.getRecordAggregationCode() == null) {
                    vaException.addActivationError(new ViewActivationError(null, c,
                            SAFRCompilerErrorType.VIEW_PROPERTIES,
                            "Record Aggregation cannot be blank."));
        		}
        	}
        }		
	}

	protected void checkSavedView() throws SAFRException {
        if (view.getId() <= 0) {
            // if the view is not saved yet then throw error
            throw new SAFRException("A new View must be saved before activation.");
        }
    }
        
    protected void checkFormatHasSortKey() {
        // if format phase is used, then it should have
        // atleast one sort key.
        if (view.isFormatPhaseInUse()) {
            if (view.getViewSortKeys().getActiveItems().isEmpty()) {
                // error. The view must have at least one sort key.
                vaException.addActivationError(new ViewActivationError(null, null,
                        SAFRCompilerErrorType.VIEW_PROPERTIES,
                        "This View must have at least one Sort Key."));
            }
        }
    }
    
    protected void checkHasViewSources() {
        // if the view doesn't have any View sources, then throw activation
        // exception.
        if (view.getViewSources().getActiveItems().isEmpty()) {
            vaException.addActivationError(new ViewActivationError(null, null,
                    SAFRCompilerErrorType.VIEW_PROPERTIES,
                    "This View must have at least one view source."));

        }
    }
    
    protected  void checkConsistentHardcopy() {
        // If the last sort key of a hard copy summary view has Display Mode
        // 'Categorize' (or blank), it must have a Sort Key Footer Option
        // of 'Print'. 
        if (isSummarizedHardCopyView()) {
            for (ViewSortKey key : view.getViewSortKeys().getActiveItems()) {
                if (isCategoryModeAndLast(key) && isSuppressPrint(key)) {
                    vaException.addActivationError(new ViewActivationError(null, key.getViewColumn(), SAFRCompilerErrorType.VIEW_PROPERTIES,
                        "Sort Key Footer Option must be 'Print' for the last sort key of a hard copy summary view."));
                    break;
                }
            }
        } 
    }

    protected boolean isSummarizedHardCopyView() {
        return view.getOutputFormat() == OutputFormat.Format_Report && 
            view.isFormatPhaseRecordAggregationOn();
    }
    
    protected boolean isCategoryModeAndLast(ViewSortKey key) {
        return key.isLastSortKey() && 
             (key.getDisplayModeCode() == null || 
              key.getDisplayModeCode().getGeneralId() == Codes.CATEGORIZE);
    }

    protected boolean isSuppressPrint(ViewSortKey key) {
        return key.getFooterOptionCode() == null
                || key.getFooterOptionCode().getGeneralId() == Codes.SUPPRESS_PRINT;
    }
    
    protected  void checkDuplicateViewSource() {
        // A view should'nt have duplicate view source.
        List<ViewSource> sources = view.getViewSources().getActiveItems();
        for (ViewSource source : sources) {
            int found = 0;
            for (ViewSource source1 : sources) {
                if (source1.getLrFileAssociationId().equals(
                        source.getLrFileAssociationId())) {
                    found++;
                }
            }
            if (found > 1) {
                // error. duplicate source found.
                vaException.addActivationError(new ViewActivationError(null, null,
                        SAFRCompilerErrorType.VIEW_PROPERTIES,
                        "Duplicate View sources are not allowed."));
                break;
            }
        }
    }

    protected void checkSortKeyTitles()
        throws SAFRException, DAOException {
        for (ViewSortKey key : view.getViewSortKeys().getActiveItems()) {
            try {
                SAFRValidator safrValidator = new SAFRValidator();
                safrValidator.verifySortKey(key);
                key.validateTitleField();
            } catch (SAFRValidationException sve) {
                for (String error : sve.getErrorMessages()) {
                    error = "Sort Key# " + key.getKeySequenceNo() + " " + error;
                    vaException.addActivationError(new ViewActivationError(null, null,
                            SAFRCompilerErrorType.VIEW_PROPERTIES, error));
                }
            }
        }
    }

    protected void initViewDependencies() {
        viewLogicDependencies = new ArrayList<ViewLogicDependency>();  
    }
        
    protected void initializeViewColumns() throws DAOException {
        // get column Ids for unsaved columns.
        getTempColumnIds();
        // calculate start positions of all the columns.
        view.calculateStartPosition();
        // calculate ordinal positions of all the columns.
        calculateOrdinalPositions();
    }
    
    /**
     * Checks for unsaved columns whose Ids are not yet assigned from DB. For
     * all such columns, this function will get DB Id and assign to id.
     * 
     * @throws DAOException
     */
    protected void getTempColumnIds() throws DAOException {
        int maxId = 1;
        List<ViewColumn> cols = new ArrayList<ViewColumn>();
        // calculate the number of Ids needed for NEW columns having non-DB Ids.
        // add all such columns to a temp list.
        for (ViewColumn col : view.getViewColumns().getActiveItems()) {
            if (col.getPersistence() == SAFRPersistence.NEW && col.getId() <= 0) {
                cols.add(col);
            } else {
                if (col.getId() > maxId) {
                    maxId = col.getId();
                }
            }
        }
        Integer idToStart = maxId+1;
        for (ViewColumn col : cols) {
            col.setId(idToStart++);
        }
    }

    /**
     * Sets the ordinal positions of all the visible columns. Invisible columns
     * will have ordinal position 0.
     */
    protected void calculateOrdinalPositions() {
        int pos = 1; // start with 1
        for (ViewColumn col : view.getViewColumns().getActiveItems()) {
            if (col.isVisible()) {
                col.setOrdinalPosition(pos++);
            } else {
                col.setOrdinalPosition(0);
            }
        }
    }

    protected void compileFormatCalculation() {        
        ViewLogicFormatCalc logCompiler = new ViewLogicFormatCalc(view, vaException);
        logCompiler.compile();
        CTCols = logCompiler.getCTCols();
    }


	protected void compileViewSources() {
		WorkbenchCompiler.reset();
		WorkbenchCompiler.setSQLConnection(DAOFactoryHolder.getDAOFactory().getConnection());
		WorkbenchCompiler.setSchema(DAOFactoryHolder.getDAOFactory().getConnectionParameters().getSchema());
//		ViewLogicExtractFilter logicExtractFilter = new ViewLogicExtractFilter(view, vaException, viewLogicDependencies);
//		ViewLogicExtractCalc logicExtractCalc = new ViewLogicExtractCalc(view, vaException, viewLogicDependencies);
//		ViewLogicExtractOutput logicExtractOutput = new ViewLogicExtractOutput(view, vaException, viewLogicDependencies);
		WorkbenchCompiler.addView(CompilerFactory.makeView(view));

		for (ViewSource source : view.getViewSources().getActiveItems()) {
			WorkbenchCompiler.addViewSource(CompilerFactory.makeViewSource(source));
			
			//This can not be a whole view thing
			//So build the whole AST
			//And the whole XLT accumulation errors etc along the way
			//in much the same way as MR91 compiles a whole view that is already in the repository
			
			//So two phases build the AST
			//Emit the XLT
			//But behind the scenes? 
			//At the end from here we just see errors or not
			//And get the dependencies
			//We can also report the whole XLT/JLT is same way as we do for Validation
				
		    WorkbenchCompiler.setEnvironment(source.getEnvironmentId());
		    WorkbenchCompiler.setSourceLRID(source.getLrFileAssociation().getAssociatingComponentId());
		    WorkbenchCompiler.setSourceLFID(source.getLrFileAssociation().getAssociatedComponentIdNum());
			compileExtractFilter(source);
			compileExtractCalculation(source, CTCols);
			compileExtractOutput(source);
			
			WorkbenchCompiler.buildTheExtractTableIfThereAreNoErrors();        
			if(WorkbenchCompiler.hasErrors()) {
				vaException.addCompilerErrorsNew(WorkbenchCompiler.getErrors(), source, null, SAFRCompilerErrorType.EXTRACT_COLUMN_ASSIGNMENT);        	
	        } else {
	        	CompilerFactory.makeLogicTableLog(WorkbenchCompiler.getXlt());
	        	ReportUtils.openReportEditor(ReportType.LogicTable);
	        }
	        if(WorkbenchCompiler.hasWarnings()) {
	        	vaException.addCompilerWarnings(WorkbenchCompiler.getWarnings(), source, null, SAFRCompilerErrorType.EXTRACT_COLUMN_ASSIGNMENT);
	        }

		}
	}
    
	protected void compileExtractFilter(ViewSource source) {
    	WBExtractFilterCompiler extractFilterCompiler = (WBExtractFilterCompiler) WBCompilerFactory.getProcessorFor(WBCompilerType.EXTRACT_FILTER);
    	extractFilterCompiler.buildAST();
	}
    
    protected void compileExtractCalculation(ViewSource source, Set<Integer> cTCols) {
    	ViewLogicExtractCalc columnsCompiler = new ViewLogicExtractCalc(view, vaException,viewLogicDependencies);
    	columnsCompiler.compile(source, cTCols);
    	//need to iterate the columns
//        CTCols = cTCols;
//        setAllSourceColumnInfo(source);
//        
//		extractColumnCompiler = (WBExtractColumnCompiler) WBCompilerFactory.getProcessorFor(WBCompilerType.EXTRACT_COLUMN);
//        for (ViewColumn col : view.getViewColumns().getActiveItems()) {
//            processExtractCalculation(source, col);
//            WorkbenchCompiler.reset();
//        }
//		WorkbenchCompiler.addColumn(getColumnData(col));
//        WBExtractColumnCompiler extractCompiler = (WBExtractColumnCompiler) WBCompilerFactory.getProcessorFor(WBCompilerType.EXTRACT_COLUMN);
//        WorkbenchCompiler.addViewColumnSource(makeViewColumnSource(view, currentSource, col, text));
//        extractCompiler.run();
//        //logicExtract.compile(source, cTCols);        
    }

	protected void compileExtractOutput(ViewSource source) {
    	WBExtractOutputCompiler extractOutputCompiler = (WBExtractOutputCompiler) WBCompilerFactory.getProcessorFor(WBCompilerType.EXTRACT_OUTPUT);
    	extractOutputCompiler.buildAST();
	}
    
    protected void compileFormatFilter()
        throws SAFRException {
        // Format phase record filter compilation
        // ONLY if format phase is ON
		if (view.isFormatPhaseInUse()) {
			if (view.getFormatRecordFilter() != null) {
				FormatFilterSyntaxChecker formatFilterChecker = (FormatFilterSyntaxChecker) WBCompilerFactory					.getProcessorFor(WBCompilerType.FORMAT_FILTER);
				formatFilterChecker.syntaxCheckLogic(view.getFormatRecordFilter());
				if (formatFilterChecker.hasSyntaxErrors()) {
					vaException.addCompilerErrorsNew(
							formatFilterChecker.getSyntaxErrors(), null, null,
							SAFRCompilerErrorType.FORMAT_RECORD_FILTER);
				}
	            Set<Integer> calcCols = formatFilterChecker.getColumnRefs();
	            checkColumnDataTypes(calcCols);
	    		CTCols.addAll(formatFilterChecker.getColumnRefs());
			}
		}
    }

	private void checkColumnDataTypes(Set<Integer> calcCols) {
		//Columns here are not indexed 
		//process the other way around - crazy slow ?
		for(ViewColumn c : view.getViewColumns()) {
			if(calcCols.contains(c.getColumnNo())) {
	    		if(c.isNumeric() == false) {
	                vaException.addActivationError(new ViewActivationError(null, null,
	                        SAFRCompilerErrorType.FORMAT_RECORD_FILTER,
	                        "Column number " +c.getColumnNo() + " is alphanumeric"));
	    		}			
	    		if(c.isSortKey()) {
	                vaException.addActivationError(new ViewActivationError(null, null,
	                        SAFRCompilerErrorType.FORMAT_RECORD_FILTER,
	                        "Column number " +c.getColumnNo() + " a sort key column"));
	    		}
			}
		}
		for(Integer refCol : calcCols) {
    		if(refCol == 0) {
                vaException.addActivationError(new ViewActivationError(null, null,
                        SAFRCompilerErrorType.FORMAT_RECORD_FILTER,
                        "Column number must be greater than zero."));
    		}						
    		if(refCol > view.getViewColumns().size()) {
                vaException.addActivationError(new ViewActivationError(null, null,
                        SAFRCompilerErrorType.FORMAT_RECORD_FILTER,
                        "Column number " + refCol + " is greater than the number of columns " + view.getViewColumns().size()));
    		}						
		}
		
	}
	
    protected void processResult() {
        if (!vaException.hasErrorOccured()) {
            view.setViewLogicDependencies(viewLogicDependencies);
            view.setStatusCode(SAFRApplication.getSAFRFactory().getCodeSet(CodeCategories.VIEWSTATUS).getCode(Codes.ACTIVE));
        }        
    }

    
    /**
     * Activate this view using the old compiler
     * 
     * @throws DAOException, SAFRException
     */     

}
