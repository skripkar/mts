/* 
 * Copyright 2012 Devoteam http://www.devoteam.com
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * 
 * 
 * This file is part of Multi-Protocol Test Suite (MTS).
 * 
 * Multi-Protocol Test Suite (MTS) is free software: you can redistribute
 * it and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of the
 * License.
 * 
 * Multi-Protocol Test Suite (MTS) is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty 
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Multi-Protocol Test Suite (MTS).
 * If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.devoteam.srit.xmlloader.core.operations;

import com.devoteam.srit.xmlloader.core.Parameter;
import com.devoteam.srit.xmlloader.core.Runner;
import com.devoteam.srit.xmlloader.core.exception.AssertException;
import com.devoteam.srit.xmlloader.core.exception.ExecutionException;
import com.devoteam.srit.xmlloader.core.exception.ExitExecutionException;
import com.devoteam.srit.xmlloader.core.exception.GotoExecutionException;
import com.devoteam.srit.xmlloader.core.log.GlobalLogger;
import com.devoteam.srit.xmlloader.core.log.TextEvent;
import com.devoteam.srit.xmlloader.core.newstats.StatKey;
import com.devoteam.srit.xmlloader.core.newstats.StatPool;
import com.devoteam.srit.xmlloader.core.utils.Config;
import com.devoteam.srit.xmlloader.core.utils.XMLElementReplacer;
import com.devoteam.srit.xmlloader.core.utils.XMLTree;

import java.io.Serializable;
import org.dom4j.Element;

public abstract class Operation implements Serializable
{

	/** Maximum number of characters to write into the log */
    private static int MAX_STRING_LENGTH = Config.getConfigByName("tester.properties").getInteger("logs.MAX_STRING_LENGTH", 1000);;

    /**
     * Name of the operation
     */
    protected String name;
    /**
     * Key of the operation (for stats feature)
     */
    protected String[] key = new String[2];

    /**
     * Object representing the XML definition of this operation
     */
    private XMLTree xmlTree;
    private boolean xmlTreeReady;

    /**
     * Constructor
     *
     * @param name Name of the operation
     */
    public Operation(Element root)
    {
        this(root, true);
    }

    public Operation(Element root, boolean recurse)
    {
        xmlTree = new XMLTree(root);
        name = root.getName();
        key[0] = name;
        key[1] = "";
        xmlTree.compute(Parameter.EXPRESSION, recurse);
        xmlTreeReady = true;
    }

    public Operation(String aName)
    {
        this.name = aName;
    }

    /**
     * Replace all elements found in operation compute(), using an XMLElementReplacer
     */
    public synchronized void restore() throws Exception
    {
        if (false == xmlTreeReady)
        {
            xmlTree.restore();
            xmlTreeReady = true;
        }
    }

    /**
     * Replace all elements found in operation compute(), using an XMLElementReplacer
     */
    public synchronized void replace(Runner runner, XMLElementReplacer replacer, TextEvent.Topic topic) throws Exception
    {
        restore();

        xmlTree.replace(replacer);

        GlobalLogger.instance().getSessionLogger().debug(runner, topic, "Operation after pre-parsing \n", this);

        xmlTreeReady = false;
    }

    @Override
    public String toString()
    {
        String string = xmlTree.toString();
        if(string.length() > MAX_STRING_LENGTH)
        {
            string = "{" + MAX_STRING_LENGTH + " of " + string.length() + "} " + string.substring(0, MAX_STRING_LENGTH);
        }
        return string;
    }

    public String getName()
    {
        return name;
    }

    public Element getRootElement()
    {
        return xmlTree.getTreeRoot();
    }

    /**
     * Returns an attribute of the root element of the XMLTree.
     * @return String
     */
    public String getAttribute(String attributeName)
    {
        return xmlTree.getTreeRoot().attributeValue(attributeName);
    }

    /**
     * Add the increments of statistic current counter
     */
    private void addStatCurrent1(Object value) throws Exception
    {
   		StatPool.getInstance().addValue(new StatKey(StatPool.PREFIX_OPERATION, this.key[0], this.key[1], "_currentNumber"), value);    		
    }

    /**
     * Add the increments of statistic end counter
     */
    private void addStatEnd1(long startTimestamp) throws Exception
    {
        addStatCurrent1(-1);
        long endTimestamp = System.currentTimeMillis();
        float duration_stats = ((float) (endTimestamp - startTimestamp) / 1000);
        StatPool.getInstance().addValue(new StatKey(StatPool.PREFIX_OPERATION, this.key[0], this.key[1], "_durationTime"), duration_stats);
        StatPool.getInstance().addValue(new StatKey(StatPool.PREFIX_OPERATION, this.key[0], this.key[1], "_completeNumber"), 1);
    }

    /**
     * Add the increments of statistic KO counter
     */
    private void addStatKO1() throws Exception
    {
   		StatPool.getInstance().addValue(new StatKey(StatPool.PREFIX_OPERATION, this.key[0], this.key[1], "_failedNumber"), 1);
    }
    /**
     * Execute operation
     *
     * @param session Current session
     * @return Next operation or null by default
     * @throws ExecutionException
     */
    public Operation executeAndStat(Runner runner) throws Exception {
        long startTimestamp = System.currentTimeMillis();
        StatPool.getInstance().addValue(new StatKey(StatPool.PREFIX_OPERATION, this.key[0], this.key[1], "_startNumber"), 1);
    	addStatCurrent1(1);

    	Operation nextOperation = null;
    	try
    	{
            // restore the XMLTree before executing the operation.
            // this is to have correct logs before the preparsing.
            if (false == xmlTreeReady)
            {
                xmlTree.restore();
                xmlTreeReady = true;
            }

            // Execute operation
    		nextOperation = execute(runner);
    	}
    	catch (AssertException e)
    	{
    		throw e;    		
    	}
    	catch (ExitExecutionException e)
    	{
    		if (e.getFailed()) {
        		addStatKO1();    			
    		}    	
    		throw e;    		
    	}
    	catch (GotoExecutionException e)
    	{
    		throw e;
    	}    	    	
    	catch (Exception e)
    	{
    		addStatKO1();
    		throw e;
    	}
        finally
        {
            addStatEnd1(startTimestamp);
        }
    	return nextOperation;
    }
    
    /**
     * Execute operation
     *
     * @param session Current session
     * @return Next operation or null by default
     * @throws ExecutionException
     */
    public abstract Operation execute(Runner runner) throws Exception;

}
