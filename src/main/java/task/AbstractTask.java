package task;

import spim.fiji.spimdata.SpimData2;

/**
 * AbstractTask contains XML filename which is used in each task
 */
public abstract class AbstractTask implements Task
{
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
}
