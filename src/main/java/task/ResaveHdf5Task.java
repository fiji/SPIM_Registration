package task;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spim.fiji.ImgLib2Temp;
import spim.fiji.plugin.queryXML.HeadlessParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.resave.Generic_Resave_HDF5;
import spim.fiji.plugin.resave.PluginHelper;
import spim.fiji.plugin.resave.ProgressWriterIJ;
import spim.fiji.plugin.resave.Resave_HDF5;
import spim.fiji.plugin.resave.Resave_TIFF;
import spim.fiji.spimdata.SpimData2;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Headless module for ResaveHdf5 task
 */
public class ResaveHdf5Task extends AbstractTask
{
	private static final Logger LOG = LoggerFactory.getLogger( ResaveHdf5Task.class );

	public String getTitle() { return "Resave to HDF5 format"; }

	public static class Parameters extends AbstractTask.Parameters
	{
		private String subSampling;
		private String chunkSize;
		private boolean useCluster;

		public String getSubSampling()
		{

			return subSampling;
		}

		public void setSubSampling( String subSampling )
		{
			this.subSampling = subSampling;
		}

		public String getChunkSize()
		{
			return chunkSize;
		}

		public void setChunkSize( String chunkSize )
		{
			this.chunkSize = chunkSize;
		}

		public boolean isUseCluster()
		{
			return useCluster;
		}

		public void setUseCluster( boolean useCluster )
		{
			this.useCluster = useCluster;
		}
	}

	private void resave( final HeadlessParseQueryXML xml, final Generic_Resave_HDF5.Parameters params)
	{
		if ( params == null )
			return;

		LoadParseQueryXML.defaultXMLfilename = params.getSeqFile().toString();

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );

		final SpimData2 data = xml.getData();
		final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted( data, xml.getViewSetupsToProcess(), xml.getTimePointsToProcess() );

		// write hdf5
		Generic_Resave_HDF5.writeHDF5( Resave_HDF5.reduceSpimData2( data, viewIds ), params, progressWriter );

		// write xml sequence description
		try
		{
			final ImgLib2Temp.Pair< SpimData2, List< String > > result = Resave_HDF5.createXMLObject( data, viewIds, params, null, false );

			xml.getIO().save( result.getA(), params.getSeqFile().getAbsolutePath() );


			// copy the interest points if they exist
			Resave_TIFF.copyInterestPoints( xml.getData().getBasePath(), params.getSeqFile().getParentFile(), result.getB() );
		}
		catch ( SpimDataException e )
		{
			LOG.info( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + params.getSeqFile() + "': " + e );
			throw new RuntimeException( e );
		}
		finally
		{
			LOG.info( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + params.getSeqFile() + "'." );
		}

		LOG.info( "done" );
	}

	public void process(Parameters params)
	{
		final HeadlessParseQueryXML xml = new HeadlessParseQueryXML();

		final String xmlFileName = params.getXmlFilename();

		if ( !xml.loadXML( xmlFileName, params.isUseCluster() ) )
			return;


		if ( Resave_HDF5.loadDimensions( xml.getData(), xml.getViewSetupsToProcess() ) )
		{
			// save the XML again with the dimensions loaded
			SpimData2.saveXML( xml.getData(), xml.getXMLFileName(), xml.getClusterExtension() );
		}

		final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = Resave_HDF5.proposeMipmaps( xml.getViewSetupsToProcess() );

		Generic_Resave_HDF5.lastExportPath = params.getXmlFilename();

		final int firstviewSetupId = xml.getData().getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
		ExportMipmapInfo autoMipmapSettings = perSetupExportMipmapInfo.get( firstviewSetupId );

		boolean lastSetMipmapManual = false;
		boolean lastSplit = false;
		int lastTimepointsPerPartition = 1;
		int lastSetupsPerPartition = 0;
		boolean lastDeflate = true;
		int lastJobIndex = 0;

		// If we are using user-defined sub-sampling config, enable the below codes.
		//
		//		String lastSubsampling = "{1,1,1}, {2,2,1}, {4,4,2}";
		//		String lastChunkSizes = "{16,16,16}, {16,16,16}, {16,16,16}";
		//		final int[][] resolutions = PluginHelper.parseResolutionsString( lastSubsampling );
		//		final int[][] subdivisions = PluginHelper.parseResolutionsString( lastChunkSizes );

		int[][] resolutions = autoMipmapSettings.getExportResolutions();
		if( params.getSubSampling() != null )
			resolutions = PluginHelper.parseResolutionsString( params.getSubSampling() );

		int[][] subdivisions = autoMipmapSettings.getSubdivisions();
		if( params.getChunkSize() != null )
			subdivisions = PluginHelper.parseResolutionsString( params.getChunkSize() );

		final File seqFile, hdf5File;

		seqFile = new File(xmlFileName);
		hdf5File = new File(seqFile.getPath().substring( 0, seqFile.getPath().length() - 4 ) + ".h5");

		int defaultConvertChoice = 1;
		double defaultMin, defaultMax;
		defaultMin = defaultMax = Double.NaN;
		boolean displayClusterProcessing = false;

		Generic_Resave_HDF5.Parameters resaveParameters = new Generic_Resave_HDF5.Parameters(
				lastSetMipmapManual, resolutions, subdivisions, seqFile, hdf5File, lastDeflate, lastSplit,
				lastTimepointsPerPartition, lastSetupsPerPartition, displayClusterProcessing, lastJobIndex,
				defaultConvertChoice, defaultMin, defaultMax );

		resave(xml, resaveParameters);
	}

	private Parameters getParams( final String[] args )
	{
		final Properties props = parseArgument( "ResaveHDF5", getTitle(), args );

		final Parameters params = new Parameters();
		params.setXmlFilename( props.getProperty( "xml_filename" ) );
		params.setSubSampling( props.getProperty( "subsampling_factors", null ) );
		params.setChunkSize( props.getProperty( "hdf5_chunk_sizes", null ) );

		return params;
	}

	@Override public void process( String[] args )
	{
		process( getParams( args ) );
	}

	public static void main( String[] argv )
	{
		ResaveHdf5Task task = new ResaveHdf5Task();
		task.process( argv );
	}
}
