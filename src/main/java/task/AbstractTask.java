package task;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spim.fiji.spimdata.SpimData2;

import java.util.Enumeration;
import java.util.Properties;

/**
 * AbstractTask contains XML filename which is used in each task
 */
public abstract class AbstractTask implements Task
{
	private static final Logger LOG = LoggerFactory.getLogger( AbstractTask.class );
	SpimData2 spimData;

	public static class Parameters
	{
		private String xmlFilename;

		/***
		 * XML file name for the dataset definition
		 */
		public String getXmlFilename()
		{
			return xmlFilename;
		}

		public void setXmlFilename( String xmlFilename )
		{
			this.xmlFilename = xmlFilename;
		}
	}

	protected void process(Parameters params)
	{
		System.out.println("Output XML File: " + params.getXmlFilename());
	}

	public SpimData2 getSpimData()
	{
		return spimData;
	}

	/**
	 * Parse argument strings and return properties.
	 *
	 * @param taskName the task name
	 * @param description the description
	 * @param args the args
	 * @return the properties
	 */
	protected Properties parseArgument( final String taskName, final String description, final String[] args )
	{
		// create Options object
		final Options options = new Options();

		final String cmdLineSyntax = taskName + " [OPTION]";

		options.addOption( Option.builder( "D" )
				.hasArgs()
				.valueSeparator( '=' )
				.desc( "use value for given property" )
				.argName( "property=value" )
				.build() );

		try
		{
			final CommandLineParser parser = new DefaultParser();
			final CommandLine cmd = parser.parse( options, args );

			final Properties props = cmd.getOptionProperties( "D" );

			if( isDebug )
			{
				Enumeration e = props.propertyNames();
				while (e.hasMoreElements()) {
					String key = (String) e.nextElement();
					System.out.println(key + " -- " + props.getProperty(key));
				}
			}

			return props;
		}
		catch ( final ParseException e )
		{
			LOG.warn( e.getMessage() );
			final HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp( cmdLineSyntax, description, options, null );
		}
		catch ( final IllegalArgumentException e )
		{
			LOG.warn( e.getMessage() );
			final HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp( cmdLineSyntax, description, options, null );
		}
		return null;
	}
}
