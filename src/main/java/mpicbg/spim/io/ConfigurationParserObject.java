/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji developers.
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

public class ConfigurationParserObject
{
	protected String entryName;
	protected String dataType;
	protected String variableName;
	protected int variableFieldPosition;
	
	public ConfigurationParserObject() 
	{
		entryName = dataType = variableName = "";
		variableFieldPosition = -1;
	}
	
	public ConfigurationParserObject( String entryName, String dataType, String variableName, int variableFieldPosition )
	{
		this.entryName = entryName;
		this.dataType = dataType;
		this.variableName = variableName;
		this.variableFieldPosition = variableFieldPosition;
	}
		
	public String getEntry() { return entryName; }
	public String getDataType() { return dataType; }
	public String getVariableName() { return variableName;	}
	public int getVariableFieldPosition() { return variableFieldPosition; }

	public void setEntry( final String entryName ) { this.entryName = entryName; }
	public void setDataType( final String dataType ) { this.dataType = dataType; }
	public void setVariableName( final String variableName ) { this.variableName = variableName; }
	public void setVariableFieldPosition( final int variableFieldPosition ) { this.variableFieldPosition = variableFieldPosition; }
	
}
