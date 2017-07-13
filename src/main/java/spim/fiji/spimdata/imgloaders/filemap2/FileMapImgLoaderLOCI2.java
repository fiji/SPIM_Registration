package spim.fiji.spimdata.imgloaders.filemap2;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.generic.sequence.ImgLoaderHints;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import spim.fiji.spimdata.imgloaders.LegacyFileMapImgLoaderLOCI;

public class FileMapImgLoaderLOCI2 implements ImgLoader
{
	private final HashMap<BasicViewDescription< ? >, Pair<File, Pair<Integer, Integer>>> fileMap;
	private final AbstractSequenceDescription<?, ?, ?> sd;
	private boolean allTimepointsInSingleFiles;
	private final IFormatReader reader;
	
	public FileMapImgLoaderLOCI2(HashMap<BasicViewDescription< ? >, Pair<File, Pair<Integer, Integer>>> fileMap,
			final ImgFactory< ? extends NativeType< ? > > imgFactory, // FIXME: remove this, only here to test quick replacement
			final AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		this.fileMap = fileMap;
		this.sd = sequenceDescription;
		
		this.reader = new ImageReader();
		
		allTimepointsInSingleFiles = true;
		
		// populate map file -> {time points}
		Map< File, Set< Integer > > tpsPerFile = new HashMap<>();
		for ( BasicViewDescription< ? > vd : fileMap.keySet() )
		{

			final File fileForVd = fileMap.get( vd ).getA();
			if ( !tpsPerFile.containsKey( fileForVd ) )
				tpsPerFile.put( fileForVd, new HashSet<>() );

			tpsPerFile.get( fileForVd ).add( vd.getTimePointId() );

			// the current file has more than one time point
			if ( tpsPerFile.get( fileForVd ).size() > 1 )
			{
				allTimepointsInSingleFiles = false;
				break;
			}

		}

		System.out.println( allTimepointsInSingleFiles );
	}
	

	@Override
	public SetupImgLoader< ? > getSetupImgLoader(int setupId)
	{
		return new FileMapSetupImgLoaderLOCI2<>(setupId);
	}
	
	public HashMap< BasicViewDescription< ? >, Pair< File, Pair< Integer, Integer > > > getFileMap()
	{
		 return fileMap;
	}
	
	public class FileMapSetupImgLoaderLOCI2 <T extends RealType<T> & NativeType< T >> implements SetupImgLoader< T >
	{
		private int setupId;

		public FileMapSetupImgLoaderLOCI2(int setupId)
		{
			this.setupId = setupId;
		}

		@Override
		public RandomAccessibleInterval< T > getImage(int timepointId, ImgLoaderHint... hints)
		{
			final BasicViewDescription< ? > vd = sd.getViewDescriptions().get( new ViewId( timepointId, setupId ) );
			final Pair< File, Pair< Integer, Integer > > imageSource = fileMap.get( vd );

			// TODO: some logging here? (reading angle .. , tp .., ... from file ...)

			RandomAccessibleInterval< T > img = null;
			try
			{
				img = new VirtualRAIFactoryLOCI().createVirtualCached( reader, imageSource.getA(), imageSource.getB().getA(), imageSource.getB().getB(), allTimepointsInSingleFiles ? 0 : timepointId );
			}
			catch ( IncompatibleTypeException e )
			{
				e.printStackTrace();
			}

			// TODO: respect load completely

			return img;
		}

		@Override
		public T getImageType()
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public RandomAccessibleInterval< FloatType > getFloatImage(int timepointId, boolean normalize,
				ImgLoaderHint... hints)
		{

			// TODO: enforce type in the factory?

			return null;
		}

		// TODO: we should already have a dimensions map when creating the img loader -> make member and use that.

		@Override
		public Dimensions getImageSize(int timepointId)
		{
			return null;
		}

		@Override
		public VoxelDimensions getVoxelSize(int timepointId)
		{
			return null;
		}

	}

}
