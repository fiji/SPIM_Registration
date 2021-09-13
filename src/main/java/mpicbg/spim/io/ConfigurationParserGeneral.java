/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2021 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package mpicbg.spim.io;

/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Diplomarbeit - IZBI Leipzig/TU Dresden</p>
 *
 * @author Stephan Preibisch
 * @version 1.0
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;

public class ConfigurationParserGeneral
{
  public static ProgramConfiguration parseFile(String confFileName) throws ConfigurationParserException
  {
	ProgramConfiguration conf = new ProgramConfiguration();

	String knownDatatypes[] = {"int","double","String","float"};

	// convert to unix-style
	confFileName = confFileName.replace('\\','/');

	// get path
	String confFilePath = "";

	try
	{
	  if (confFileName.indexOf("/") != -1)
		  confFilePath = confFileName.substring(0, confFileName.lastIndexOf("/") + 1);
	  else
		confFilePath = "";
	}
	catch (Exception e)
	{
	  throw new ConfigurationParserException("Error parsing confFileName-String: '"+e.getMessage()+"'");
	}


	// open configfile
	BufferedReader assignFile = null;
	BufferedReader confFile = null;
	try
	{
		confFile = TextFileAccess.openFileReadEx(confFileName);
	}
	catch ( IOException e )
	{
		throw new ConfigurationParserException("Configuration file not found: '"+confFileName+"'");
	}	  

	// open assignment file
	try
	{
	  while (confFile.ready() && assignFile == null)
	  {
		String line = confFile.readLine().trim();

		if (line.startsWith("<") && line.endsWith(">"))
		{
		  assignFile = TextFileAccess.openFileRead(confFilePath + line.substring(1,line.length()-1));

		  if (assignFile == null)
			throw new ConfigurationParserException("Variables Assignment file not found: '"+(line.substring(1,line.length()-2))+"'");
		}
	  }
	}  catch (Exception e)
	{
	  throw new ConfigurationParserException("Error finding assignment file entry: '"+e.getMessage()+"'");
	}

	// load assignments
	ArrayList< Object > assignments = new ArrayList< Object >();
	Field[] fields = conf.getClass().getDeclaredFields();

	int lineCount = 0;
	try
	{
	  while (assignFile.ready())
	  {
		lineCount++;
		String line = assignFile.readLine().trim();

		if (!line.startsWith("#") && line.length() > 0)
		{
		  String words[] = line.split("=");

		  if (words.length != 2)
			throw new ConfigurationParserException("Wrong format in assignment file, should be 'entry = datatype name'");

		  // entry name
		  ArrayList< Object > temp = new ArrayList< Object >();
		  temp.add(words[0].trim());

		  words = words[1].trim().split(" ");
		  if (words.length != 2)
			throw new ConfigurationParserException("Wrong format in assignment file, datatype and name on right side MUST have no spaces");

		  words[0] = words[0].trim();
		  words[1] = words[1].trim();

		  // datatype
		  boolean positiveMatch = false;

		  for (int i = 0; i < knownDatatypes.length; i++)
		  {
			if (words[0].compareTo(knownDatatypes[i]) == 0)
			  positiveMatch = true;
		  }

		  if (!positiveMatch)
		  {
			String datatypes = "";
			for (int i = 0; i < knownDatatypes.length; i++)
			  datatypes += knownDatatypes[i] + " ";

			throw new ConfigurationParserException("Unknown datatype '" + words[0] + "', available datatypes are: " + datatypes);
		  }

		  temp.add(words[0]);

		  // variable name
		  int variablesPosition = -1;

		  for (int i = 0; i < fields.length; i++)
		  {
			if (words[1].compareTo(fields[i].getName()) == 0)
			  variablesPosition = i;
		  }

		  if (variablesPosition == -1)
		  {
			String variables = "";
			for (int i = 0; i < fields.length; i++)
			  variables += fields[i].getName() + "\n";

			throw new ConfigurationParserException("Unknown variable '" + words[1] + "', available variables are:\n" + variables);
		  }

		  temp.add(words[1]);
		  temp.add(new Integer(variablesPosition));

		  assignments.add(temp);
		}
	  }
	}
	catch (Exception e)
	{
	  throw new ConfigurationParserException("Error reading/parsing assignment file at line "+lineCount+":\n"+e.getMessage());
	}

	// read configuration file and assign entry values to assigned variables
	lineCount = 0;

	try
	{
	  while (confFile.ready())
	  {
		lineCount++;
		String line = confFile.readLine().trim();

		if (!line.startsWith("#") && line.length() > 0)
		{
		  String words[] = line.split("=");

		  if (words.length != 2)
			throw new ConfigurationParserException("Wrong format in configuration file, should be 'entry = value'");

		  words[0] = words[0].trim();
		  words[1] = words[1].trim();

		  int entryPos = findEntry(assignments,words[0]);

		  if (entryPos == -1)
			throw new ConfigurationParserException("Entry '"+words[0]+"' does not exist!\nFollowing entries are available:\n"+getAllEntries(assignments));

		  int varFieldPos = getVariableFieldPosition(assignments,entryPos);

		  if (getDatatype(assignments,entryPos).compareTo("int") == 0)
		  {
			fields[varFieldPos].setInt(conf, Integer.parseInt(words[1]));
		  }
		  else if (getDatatype(assignments,entryPos).compareTo("double") == 0)
		  {
			if (words[1].toLowerCase().compareTo("nan") == 0)
			  fields[varFieldPos].setDouble(conf, Double.NaN);
			else
			  fields[varFieldPos].setDouble(conf, Double.parseDouble(words[1]));
		  }
		  else if (getDatatype(assignments,entryPos).compareTo("String") == 0)
		  {
			if (words[1].startsWith("\"") && words[1].endsWith("\""))
			  fields[varFieldPos].set(conf,words[1].substring(1,words[1].length()-1));
			else
			  throw new ConfigurationParserException("Strings have to be surrounded by  \"\"");
		  }
		  else if (getDatatype(assignments,entryPos).compareTo("float") == 0)
		  {
			if (words[1].toLowerCase().compareTo("nan") == 0)
			  fields[varFieldPos].setFloat(conf, Float.NaN);
			else
			  fields[varFieldPos].setFloat(conf, Float.parseFloat(words[1]));
		  }
		  else
		  {
			throw new ConfigurationParserException("Unknown datatype '"+getDatatype(assignments,entryPos)+"'");
		  }

		}
	  }
	}
	catch (Exception e)
	{
	  e.printStackTrace();
	  throw new ConfigurationParserException("Error reading/parsing configuration file at line "+lineCount+":\n"+e.getMessage());
	}

	// variable specific verification
	
	// check the directory string
	conf.baseFolder = conf.baseFolder.replace('\\', '/');
	conf.baseFolder = conf.baseFolder.trim();
	if (conf.baseFolder.length() > 0 && !conf.baseFolder.endsWith("/"))
		conf.baseFolder = conf.baseFolder + "/";

	// check the directory string
	conf.binariesFolder = conf.binariesFolder.replace('\\', '/');
	conf.binariesFolder = conf.binariesFolder.trim();
	if (conf.binariesFolder.length() > 0 && !conf.binariesFolder.endsWith("/"))
		conf.binariesFolder = conf.binariesFolder + "/";

	// check the directory string
	conf.librariesFolder = conf.librariesFolder.replace('\\', '/');
	conf.librariesFolder = conf.librariesFolder.trim();
	if (conf.librariesFolder.length() > 0 && !conf.librariesFolder.endsWith("/"))
		conf.librariesFolder = conf.librariesFolder + "/";

	// check the directory string
	conf.jobFolder = conf.jobFolder.replace('\\', '/');
	conf.jobFolder = conf.jobFolder.trim();
	if (conf.jobFolder.length() > 0 && !conf.jobFolder.endsWith("/"))
		conf.jobFolder = conf.jobFolder + "/";

	// check the directory string
	conf.configFolder = conf.configFolder.replace('\\', '/');
	conf.configFolder = conf.configFolder.trim();
	if (conf.configFolder.length() > 0 && !conf.configFolder.endsWith("/"))
		conf.configFolder = conf.configFolder + "/";

	conf.parseTimePoints();	
	
	// close files
	try
	{
	  assignFile.close();
	  confFile.close();
	}
	catch (Exception e){};

	return conf;
  }

  private static int findEntry(ArrayList< Object > list, String entry)
  {
	int pos = -1;

	for (int i = 0; i < list.size() && pos == -1; i++)
	{
	  if ((getEntry(list,i).toLowerCase()).compareTo(entry.toLowerCase()) == 0)
		pos = i;
	}

	return pos;
  }

  private static String getAllEntries(ArrayList< Object > list)
  {
	String entries = "";
	for (int i = 0; i < list.size(); i++)
	{
	  entries += getEntry(list,i) + "\n";
	}

	return entries;
  }

  private static String getEntry(ArrayList< Object > list, int pos)
  {
	return (String)(((ArrayList<?>)list.get(pos)).get(0));
  }

  private static String getDatatype(ArrayList< Object > list, int pos)
  {
	return (String)(((ArrayList<?>)list.get(pos)).get(1));
  }

  private static int getVariableFieldPosition(ArrayList< Object > list, int pos)
  {
	return ((Integer)(((ArrayList<?>)list.get(pos)).get(3))).intValue();
  }

}
