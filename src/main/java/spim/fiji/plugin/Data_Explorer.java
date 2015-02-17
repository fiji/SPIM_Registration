package spim.fiji.plugin;

import ij.plugin.PlugIn;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.explorer.ViewSetupExplorer;
import spim.fiji.spimdata.explorer.interestpoint.InterestPointExplorer;
import spim.fiji.spimdata.explorer.registration.RegistrationExplorer;

public class Data_Explorer implements PlugIn
{
	boolean newDataset = false;

	@Override
	public void run( String arg )
	{
		final LoadParseQueryXML result = new LoadParseQueryXML();

		result.addButton( "Define a new dataset", new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				result.setReturnFalse( true );
				result.getGenericDialog().dispose();
				setDefineNewDataset();
			}
		});

		if ( !result.queryXML( "XML Explorer", "", false, false, false, false ) && !newDataset )
			return;

		final SpimData2 data;
		final String xml;
		final XmlIoSpimData2 io;

		if ( newDataset )
		{
			final Pair< SpimData2, String > dataset = new Define_Multi_View_Dataset().defineDataset( true );

			if ( dataset == null )
				return;

			data = dataset.getA();
			xml = dataset.getB();
			io = new XmlIoSpimData2( "" );
		}
		else
		{
			data = result.getData();
			xml = result.getXMLFileName();
			io = result.getIO();
		}

		final ViewSetupExplorer< SpimData2, XmlIoSpimData2 > explorer = new ViewSetupExplorer<SpimData2, XmlIoSpimData2 >( data, xml, io );

		new InterestPointExplorer< SpimData2, XmlIoSpimData2 >( data, xml, io, explorer );
		new RegistrationExplorer< SpimData2, XmlIoSpimData2 >( data, xml, io, explorer );
	}

	protected void setDefineNewDataset()
	{
		this.newDataset = true;
	}

	public static void main( String[] args )
	{
		GenericLoadParseQueryXML.defaultXMLfilename = "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";
		new Data_Explorer().run( null );
	}
}
