package spim.fiji.plugin.resave;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import spim.fiji.plugin.Toggle_Cluster_Options;
import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;
import bdv.export.SubTaskProgressWriter;
import bdv.export.WriteSequenceToHdf5;
import bdv.export.WriteSequenceToHdf5.DefaultLoopbackHeuristic;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;

public class Generic_Resave_HDF5 implements PlugIn
{
	public static void main( final String[] args )
	{
		new Generic_Resave_HDF5().run( null );
	}

	public static class Parameters
	{
		boolean setMipmapManual;
		int[][] resolutions;
		int[][] subdivisions;
		File seqFile;
		File hdf5File;
		boolean deflate;
		boolean split;
		int timepointsPerPartition;
		int setupsPerPartition;
		boolean onlyRunSingleJob;
		int jobId;

		public Parameters(
				final boolean setMipmapManual, final int[][] resolutions, final int[][] subdivisions,
				final File seqFile, final File hdf5File,
				final boolean deflate,
				final boolean split, final int timepointsPerPartition, final int setupsPerPartition,
				final boolean onlyRunSingleJob, final int jobId )
		{
			this.setMipmapManual = setMipmapManual;
			this.resolutions = resolutions;
			this.subdivisions = subdivisions;
			this.seqFile = seqFile;
			this.hdf5File = hdf5File;
			this.deflate = deflate;
			this.split = split;
			this.timepointsPerPartition = timepointsPerPartition;
			this.setupsPerPartition = setupsPerPartition;
			this.onlyRunSingleJob = onlyRunSingleJob;
			this.jobId = jobId;
		}

		public void setSeqFile( final File seqFile ) { this.seqFile = seqFile; }
		public void setHDF5File( final File hdf5File ) { this.hdf5File = hdf5File; }
		public void setResolutions( final int[][] resolutions ) { this.resolutions = resolutions; }
		public void setSubdivisions( final int[][] subdivisions ) { this.subdivisions = subdivisions; }
		public void setMipmapManual( final boolean setMipmapManual ) { this.setMipmapManual = setMipmapManual; }
		public void setDeflate( final boolean deflate ) { this.deflate = deflate; }
		public void setSplit( final boolean split ) { this.split = split; }
		public void setTimepointsPerPartition( final int timepointsPerPartition ) { this.timepointsPerPartition = timepointsPerPartition; }
		public void setSetupsPerPartition( final int setupsPerPartition ) { this.setupsPerPartition = setupsPerPartition; }

		public File getSeqFile() { return seqFile; }
		public File getHDF5File() { return hdf5File; }
		public int[][] getResolutions() { return resolutions; }
		public int[][] getSubdivisions() { return subdivisions; }
		public boolean getMipmapManual() { return setMipmapManual; }
		public boolean getDeflate() { return deflate; }
		public boolean getSplit() { return split; }
		public int getTimepointsPerPartition() { return timepointsPerPartition; }
		public int getSetupsPerPartition() { return setupsPerPartition; }
	}

	@Override
	public void run( final String arg )
	{
		final File file = getInputXML();
		if ( file == null )
			return;

		final XmlIoSpimDataMinimal io = new XmlIoSpimDataMinimal();
		SpimDataMinimal spimData;
		try
		{
			spimData = io.load( file.getAbsolutePath() );
		}
		catch ( final SpimDataException e )
		{
			throw new RuntimeException( e );
		}

		final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = ProposeMipmaps.proposeMipmaps( spimData.getSequenceDescription() );

		final int firstviewSetupId = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
		final Parameters params = getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), true );
		if ( params == null )
			return;

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );

		// write hdf5
		writeHDF5( spimData, params, progressWriter );

		// write xml sequence description
		try
		{
			writeXML( spimData, io, params, progressWriter );
		}
		catch ( SpimDataException e )
		{
			throw new RuntimeException( e );
		}
	}

	public static Map< Integer, ExportMipmapInfo > getPerSetupExportMipmapInfo( final AbstractSpimData< ? > spimData, final Parameters params )
	{
		if ( params.setMipmapManual )
		{
			final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = new HashMap< Integer, ExportMipmapInfo >();
			final ExportMipmapInfo mipmapInfo = new ExportMipmapInfo( params.resolutions, params.subdivisions );
			for ( final BasicViewSetup setup : spimData.getSequenceDescription().getViewSetupsOrdered() )
				perSetupExportMipmapInfo.put( setup.getId(), mipmapInfo );
			return perSetupExportMipmapInfo;
		}
		else
			return ProposeMipmaps.proposeMipmaps( spimData.getSequenceDescription() );
	}

	public static ArrayList< Partition > getPartitions( final AbstractSpimData< ? > spimData, final Parameters params )
	{
		final AbstractSequenceDescription< ?, ?, ? > seq = spimData.getSequenceDescription();
		if ( params.split )
		{
			final String xmlFilename = params.seqFile.getAbsolutePath();
			final String basename = xmlFilename.endsWith( ".xml" ) ? xmlFilename.substring( 0, xmlFilename.length() - 4 ) : xmlFilename;
			List< TimePoint > timepoints = seq.getTimePoints().getTimePointsOrdered();
			List< ? extends BasicViewSetup > setups = seq.getViewSetupsOrdered();
			return Partition.split( timepoints, setups, params.timepointsPerPartition, params.setupsPerPartition, basename );
		}
		else
			return null;
	}

	public static void writeHDF5( final AbstractSpimData< ? > spimData, final Parameters params, final ProgressWriter progressWriter )
	{
		Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = getPerSetupExportMipmapInfo( spimData, params );
		final ArrayList< Partition > partitions = getPartitions( spimData, params );
		AbstractSequenceDescription< ?, ?, ? > seq = spimData.getSequenceDescription();
		if ( partitions != null )
		{
			for ( int i = 0; i < partitions.size(); ++i )
			{
				final Partition partition = partitions.get( i );
				final ProgressWriter p = new SubTaskProgressWriter( progressWriter, 0, 0.95 * i / partitions.size() );
				progressWriter.out().printf( "proccessing partition %d / %d\n", ( i + 1 ), partitions.size() );
				if ( !params.onlyRunSingleJob || params.jobId == i + 1 )
					WriteSequenceToHdf5.writeHdf5PartitionFile( seq, perSetupExportMipmapInfo, params.deflate, partition, new DefaultLoopbackHeuristic(), null, p );
			}
			if ( !params.onlyRunSingleJob || params.jobId == 0 )
				WriteSequenceToHdf5.writeHdf5PartitionLinkFile( seq, perSetupExportMipmapInfo, partitions, params.hdf5File );
		}
		else
		{
			final ProgressWriter p = new SubTaskProgressWriter( progressWriter, 0, 0.95 );
			WriteSequenceToHdf5.writeHdf5File( seq, perSetupExportMipmapInfo, params.deflate, params.hdf5File, new DefaultLoopbackHeuristic(), null, p );
		}
	}

	public static < T extends AbstractSpimData< A >, A extends AbstractSequenceDescription< ?, ?, ? super ImgLoader< ? > > > void writeXML(
			final T spimData,
			final XmlIoAbstractSpimData< A, T > io,
			final Parameters params,
			final ProgressWriter progressWriter )
		throws SpimDataException
	{
		final A seq = spimData.getSequenceDescription();
		final ArrayList< Partition > partitions = getPartitions( spimData, params );
		final Hdf5ImageLoader hdf5Loader = new Hdf5ImageLoader( params.hdf5File, partitions, null, false );
		seq.setImgLoader( hdf5Loader );
		spimData.setBasePath( params.seqFile.getParentFile() );
		try
		{
			if ( !params.onlyRunSingleJob || params.jobId == 0 )
				io.save( spimData, params.seqFile.getAbsolutePath() );
			progressWriter.setProgress( 1.0 );
		}
		catch ( final Exception e )
		{
			progressWriter.err().println( "Failed to write xml file " + params.seqFile );
			e.printStackTrace( progressWriter.err() );
		}
		progressWriter.out().println( "done" );
	}

	static boolean lastSetMipmapManual = false;

	static String lastSubsampling = "{1,1,1}, {2,2,1}, {4,4,2}";

	static String lastChunkSizes = "{16,16,16}, {16,16,16}, {16,16,16}";

	static boolean lastSplit = false;

	static int lastTimepointsPerPartition = 1;

	static int lastSetupsPerPartition = 0;

	static boolean lastDeflate = true;

	static int lastJobIndex = 0;

	public static String lastExportPath = "/Users/pietzsch/Desktop/spimrec2.xml";

	public static File getInputXML()
	{
		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileFilter( new FileFilter()
		{
			@Override
			public String getDescription()
			{
				return "xml files";
			}

			@Override
			public boolean accept( final File f )
			{
				if ( f.isDirectory() )
					return true;
				if ( f.isFile() )
				{
					final String s = f.getName();
					final int i = s.lastIndexOf('.');
					if (i > 0 &&  i < s.length() - 1) {
						final String ext = s.substring(i+1).toLowerCase();
						return ext.equals( "xml" );
					}
				}
				return false;
			}
		} );

		if ( fileChooser.showOpenDialog( null ) == JFileChooser.APPROVE_OPTION )
			return fileChooser.getSelectedFile();
		else
			return null;
	}

	public static Parameters getParameters( final ExportMipmapInfo autoMipmapSettings, final boolean askForXMLPath )
	{
		return getParameters( autoMipmapSettings, askForXMLPath, "Export for BigDataViewer" );
	}

	public static Parameters getParameters( final ExportMipmapInfo autoMipmapSettings, final boolean askForXMLPath, final String dialogTitle )
	{
		final boolean displayClusterProcessing = Toggle_Cluster_Options.displayClusterProcessing;
		if ( displayClusterProcessing )
		{
			lastSplit = true;
			lastTimepointsPerPartition = 1;
			lastSetupsPerPartition = 0;
		}

		while ( true )
		{
			final GenericDialogPlus gd = new GenericDialogPlus( dialogTitle );

			gd.addCheckbox( "manual_mipmap_setup", lastSetMipmapManual );
			final Checkbox cManualMipmap = ( Checkbox ) gd.getCheckboxes().lastElement();
			gd.addStringField( "Subsampling_factors", lastSubsampling, 25 );
			final TextField tfSubsampling = ( TextField ) gd.getStringFields().lastElement();
			gd.addStringField( "Hdf5_chunk_sizes", lastChunkSizes, 25 );
			final TextField tfChunkSizes = ( TextField ) gd.getStringFields().lastElement();

			gd.addMessage( "" );
			gd.addCheckbox( "split_hdf5", lastSplit );
			final Checkbox cSplit = ( Checkbox ) gd.getCheckboxes().lastElement();
			gd.addNumericField( "timepoints_per_partition", lastTimepointsPerPartition, 0, 25, "" );
			final TextField tfSplitTimepoints = ( TextField ) gd.getNumericFields().lastElement();
			gd.addNumericField( "setups_per_partition", lastSetupsPerPartition, 0, 25, "" );
			final TextField tfSplitSetups = ( TextField ) gd.getNumericFields().lastElement();
			if ( displayClusterProcessing )
			{
				gd.addNumericField( "run_only_job_number", lastJobIndex, 0, 25, "" );
			}

			gd.addMessage( "" );
			gd.addCheckbox( "use_deflate_compression", lastDeflate );


			if ( askForXMLPath )
			{
				gd.addMessage( "" );
				PluginHelper.addSaveAsFileField( gd, "Export_path", lastExportPath, 25 );
			}

			final String autoSubsampling = ProposeMipmaps.getArrayString( autoMipmapSettings.getExportResolutions() );
			final String autoChunkSizes = ProposeMipmaps.getArrayString( autoMipmapSettings.getSubdivisions() );
			gd.addDialogListener( new DialogListener()
			{
				@Override
				public boolean dialogItemChanged( final GenericDialog dialog, final AWTEvent e )
				{
					gd.getNextBoolean();
					gd.getNextString();
					gd.getNextString();
					gd.getNextBoolean();
					gd.getNextNumber();
					gd.getNextNumber();
					if ( displayClusterProcessing )
						gd.getNextNumber();
					gd.getNextBoolean();
					if ( askForXMLPath )
						gd.getNextString();
					if ( e instanceof ItemEvent && e.getID() == ItemEvent.ITEM_STATE_CHANGED && e.getSource() == cManualMipmap )
					{
						final boolean useManual = cManualMipmap.getState();
						tfSubsampling.setEnabled( useManual );
						tfChunkSizes.setEnabled( useManual );
						if ( !useManual )
						{
							tfSubsampling.setText( autoSubsampling );
							tfChunkSizes.setText( autoChunkSizes );
						}
					}
					else if ( e instanceof ItemEvent && e.getID() == ItemEvent.ITEM_STATE_CHANGED && e.getSource() == cSplit )
					{
						final boolean split = cSplit.getState();
						tfSplitTimepoints.setEnabled( split );
						tfSplitSetups.setEnabled( split );
					}
					return true;
				}
			} );

			tfSubsampling.setEnabled( lastSetMipmapManual );
			tfChunkSizes.setEnabled( lastSetMipmapManual );
			if ( !lastSetMipmapManual )
			{
				tfSubsampling.setText( autoSubsampling );
				tfChunkSizes.setText( autoChunkSizes );
			}

			tfSplitTimepoints.setEnabled( lastSplit );
			tfSplitSetups.setEnabled( lastSplit );

			if ( displayClusterProcessing )
			{
				cSplit.setEnabled( false );
				tfSplitTimepoints.setEnabled( false );
				tfSplitSetups.setEnabled( false );
			}

			gd.showDialog();
			if ( gd.wasCanceled() )
				return null;

			lastSetMipmapManual = gd.getNextBoolean();
			lastSubsampling = gd.getNextString();
			lastChunkSizes = gd.getNextString();
			lastSplit = gd.getNextBoolean();
			lastTimepointsPerPartition = ( int ) gd.getNextNumber();
			lastSetupsPerPartition = ( int ) gd.getNextNumber();
			if ( displayClusterProcessing )
			{
				lastJobIndex = ( int ) gd.getNextNumber();
			}
			lastDeflate = gd.getNextBoolean();
			if ( askForXMLPath )
				lastExportPath = gd.getNextString();

			// parse mipmap resolutions and cell sizes
			final int[][] resolutions = PluginHelper.parseResolutionsString( lastSubsampling );
			final int[][] subdivisions = PluginHelper.parseResolutionsString( lastChunkSizes );
			if ( resolutions.length == 0 )
			{
				IJ.showMessage( "Cannot parse subsampling factors " + lastSubsampling );
				continue;
			}
			if ( subdivisions.length == 0 )
			{
				IJ.showMessage( "Cannot parse hdf5 chunk sizes " + lastChunkSizes );
				continue;
			}
			else if ( resolutions.length != subdivisions.length )
			{
				IJ.showMessage( "subsampling factors and hdf5 chunk sizes must have the same number of elements" );
				continue;
			}

			final File seqFile, hdf5File;

			if ( askForXMLPath )
			{
				String seqFilename = lastExportPath;
				if ( !seqFilename.endsWith( ".xml" ) )
					seqFilename += ".xml";
				seqFile = new File( seqFilename );
				final File parent = seqFile.getParentFile();
				if ( parent == null || !parent.exists() || !parent.isDirectory() )
				{
					IJ.showMessage( "Invalid export filename " + seqFilename );
					continue;
				}
				final String hdf5Filename = seqFilename.substring( 0, seqFilename.length() - 4 ) + ".h5";
				hdf5File = new File( hdf5Filename );
			}
			else
			{
				seqFile = hdf5File = null;
			}

			return new Parameters( lastSetMipmapManual, resolutions, subdivisions, seqFile, hdf5File, lastDeflate, lastSplit, lastTimepointsPerPartition, lastSetupsPerPartition, displayClusterProcessing, lastJobIndex );
		}
	}
}