package spim.fiji.plugin;

import ij.ImageJ;
import ij.plugin.PlugIn;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.explorer.SimpleInfoBox;
import spim.fiji.spimdata.explorer.ViewSetupExplorer;

public class Data_Explorer implements PlugIn
{
	public static boolean showNote = true;
	boolean newDataset = false;

	@Override
	public void run( String arg )
	{
		if ( showNote )
		{
			showNote();
			showNote = false;
		}

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

		explorer.getFrame().toFront();

		
	}

	public static SimpleInfoBox showNote()
	{
		String text = "Welcome to the Multiview Reconstruction Software!\n\n";

		text += "Here are a few tipps & tricks that hopefully get you started. The first thing you should do is to\n";
		text += "have a look at the online documentation, which is growing (http://fiji.sc/Multiview-Reconstruction).\n\n";

		text += "For newcomers, the basic steps you need to do are the following:\n";
		text += "1) Define a new dataset in the open dialog, which will create the XML and open an explorer window\n";
		text += "2) Select one of the views and make sure it displays right in ImageJ\n";
		text += "3) Consider converting your dataset to HDF5, as it makes it possible to use the BigDataViewer\n" + 
				"to browse the entire dataset interactively\n";
		text += "4) Detect interest points in your views (could be beads, nuclei, ...)\n";
		text += "5) Register your data using those interest points (rotation invariant)\n";
		text += "6) Fuse or deconvolve the dataset\n";
		text += "\n";

		text += "Please note that the outlined steps above should work out of the box it you have fluoresecent beads\n";
		text += "sourrounding your sample. If you want to use sample features like nuclei, you need to apply approximate\n";
		text += "transformations first (known rotation axis & angles) and register using translation-invariant matching.\n";
		text += "\n";
		text += "Tipp: If you get too many detections inside the sample and you just want to find beads, you can remove\n";
		text += "them based on their distance to each other (Remove Interest Points > By Distance ...) - remove all that\n";
		text += "too close to each other (e.g. less than 5 pixels)\n";

		return new SimpleInfoBox( "Getting started", text );
	}

	protected void setDefineNewDataset()
	{
		this.newDataset = true;
	}

	public static void main( String[] args )
	{
		new ImageJ();

		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";

		new Data_Explorer().run( null );
	}
}
