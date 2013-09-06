package fiji.datasetmanager;

import java.util.ArrayList;

import mpicbg.spim.io.ConfigurationParserException;

public class IntegerPattern 
{
	/**
	 * Analyzes a file pattern and returns the number of digits as well as the replace pattern
	 * for a certain type, 
	 * e.g. analyze spim_TL{t}_Angle{aaa}.tif for how many 'a' need to be replaced. It would 
	 * return {aaa}, so you know it is with leading zeros up to a length of 3.
	 * 
	 * @param inputFilePattern - e.g. spim_TL{t}_Angle{aaa}.tif
	 * @param type - e.g. 't' or 'a'
	 * @return the replace pattern "{t}" or "{aaa}", null if {type} does not exist in the String
	 */
    public static String getReplaceString( final String inputFilePattern, final char type )
    {
    	String replacePattern = null;
    	int numDigitsTL = 0;

		final int i1 = inputFilePattern.indexOf( "{" + type );
		final int i2 = inputFilePattern.indexOf( type + "}" );
		if (i1 >= 0 && i2 > 0)
		{
			replacePattern = "{";

			numDigitsTL = i2 - i1;
			
			for (int i = 0; i < numDigitsTL; i++)
				replacePattern += type;

			replacePattern += "}";
		}

		return replacePattern;
    }

	/**
	 * Parse a pattern provided by the user defining a range of integers. Allowed are enumerations seperated by
	 * commas, each entry can be a single number, a range e.g. 4-100 or a range in intervals e.g. 0-30:10 - which
	 * is equivalent to 0,10,20,30
	 * 
	 * @param integers - the input
	 * @param description - for the error message only
	 * @return a list of integers that were described, an empty list with the entry 0 if the String is "" or null
	 * @throws ConfigurationParserException if the input string was illegal
	 */
    public static ArrayList<Integer> parseIntegerString( final String integers, final String description ) throws ConfigurationParserException
    {
    	ArrayList<Integer> tmp = null;

    	if ( integers == null || integers.trim().length() == 0 )
    	{
    		tmp = new ArrayList<Integer>();
    		tmp.add( 0 );
    		return tmp;
    	}
    	
		try
		{
	    	tmp = new ArrayList<Integer>();
	    	final String[] entries = integers.split( "," );
	    	
	    	for ( String s : entries )
	    	{
	    		s = s.replaceAll( " ", "" );
	    		s = s.trim();

	    		if ( s.contains( "-" ) )
	    		{
	    			int start = 0, end = 0, step;
	    			start = Integer.parseInt(s.substring(0, s.indexOf("-")));

	    			if ( s.indexOf(":") < 0)
	    			{
	    				end = Integer.parseInt(s.substring(s.indexOf("-") + 1, s.length()));
	    				step = 1;
	    			}
	    			else
	    			{
	    				end = Integer.parseInt(s.substring(s.indexOf("-") + 1, s.indexOf(":")));
	    				step = Integer.parseInt(s.substring(s.indexOf(":") + 1, s.length()));
	    			}

	    			if (end > start)
	    				for (int i = start; i <= end; i += step)
	    					tmp.add(i);
	    			else
	    				for (int i = start; i >= end; i -= step)
	    					tmp.add(i);
	    		}
	    		else
	    		{
	    			tmp.add(Integer.parseInt(s));
	    		}
	    	}
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
			throw new ConfigurationParserException( "Cannot parse: " + integers + "(" + description + ")" );
		}

		return tmp;
    }

}
