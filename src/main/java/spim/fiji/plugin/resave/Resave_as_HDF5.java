package spim.fiji.plugin.resave;

import ij.plugin.PlugIn;

import java.util.Date;
import java.util.Map;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.resave.Generic_Resave_HDF5.Parameters;
import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;

public class Resave_as_HDF5 implements PlugIn
{

	@Override
	public void run( final String arg0 )
	{
		final LoadParseQueryXML xml = new LoadParseQueryXML();
		
		if ( !xml.queryXML() )
			return;
		
		final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = ProposeMipmaps.proposeMipmaps( xml.getData().getSequenceDescription() );

		Generic_Resave_HDF5.lastExportPath = LoadParseQueryXML.defaultXMLfilename;
		final Parameters params = Generic_Resave_HDF5.getParameters( perSetupExportMipmapInfo.get( 0 ), true );
		if ( params == null )
			return;

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );

		// write hdf5
		Generic_Resave_HDF5.writeHDF5( xml.getData().getSequenceDescription(), params, perSetupExportMipmapInfo, progressWriter );
		
		// write xml sequence description
		try
		{
			Generic_Resave_HDF5.writeXML( xml.getData(), xml.getIO(), params.getSeqFile(), params.getHDF5File(), progressWriter );
		} 
		catch ( SpimDataException e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + xml + "': " + e );
			throw new RuntimeException( e );
		}
		finally
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + xml + "'." );
		}
	}

}
